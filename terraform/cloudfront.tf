# =============================================================================
# CloudFront Distribution -- Portfolio CDN
# =============================================================================
# Existing CloudFront distribution serving portfolio images from S3.
# Uses Origin Access Control (OAC) -- the modern replacement for OAI (deprecated 2022).
# The S3 bucket policy grants access via cloudfront.amazonaws.com service principal
# conditioned on the distribution ARN (OAC pattern).
#
# Import:
#   terraform import aws_cloudfront_origin_access_control.portfolio E12HK3694LNYG0
#   terraform import aws_cloudfront_distribution.portfolio E2SR03MLB2ZFMR
#   terraform import aws_s3_bucket_policy.cloudfront_access edens.zac.portfolio
# =============================================================================

resource "aws_cloudfront_origin_access_control" "portfolio" {
  name                              = "Portfolio-Images"
  description                       = "OAI For Portfolio Images"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Bucket policy granting CloudFront OAC read access to S3
resource "aws_s3_bucket_policy" "cloudfront_access" {
  bucket = aws_s3_bucket.portfolio.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontServicePrincipal"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.portfolio.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.portfolio.arn
          }
        }
      }
    ]
  })
}

# =============================================================================
# API read surface -- /api/read/* behind the same distribution
# =============================================================================
# Collapses repeat reads at the edge instead of running SQL on EC2 for every page
# open. Freshness is decided entirely by the origin's Cache-Control headers (see
# CacheControlInterceptor / ReadCachePolicy); nothing here overrides them.
#
# All of this is conditional on var.api_origin_domain. Unset, the distribution is
# byte-for-byte what it is today.
# =============================================================================

locals {
  api_origin_id = "${var.project_name}-api-origin"
}

# Managed policies, resolved by name rather than hardcoded UUID so the intent is legible.
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

# Forwards every viewer header, cookie, and query string EXCEPT Host. Host is excluded
# deliberately: Caddy on EC2 serves a specific vhost, and forwarding the viewer's Host
# (the CloudFront domain) would not match it.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_cache_policy" "api_read" {
  count = var.api_origin_domain == "" ? 0 : 1

  name    = "${var.project_name}-api-read"
  comment = "Cache key for /api/read/*: query strings + internal secret, no cookies."

  # min/default 0 with a long max hands freshness entirely to the origin: a response
  # marked no-store is never stored, and one marked s-maxage=300 lives exactly 300s.
  min_ttl     = 0
  default_ttl = 0
  max_ttl     = 31536000

  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true

    # Pagination and search live in the query string (page, size, collectionPage,
    # imagePage, ...). Omitting them would serve page 1 for every page.
    query_strings_config {
      query_string_behavior = "all"
    }

    # X-Internal-Secret is in the CACHE KEY, not injected by CloudFront, and this is
    # deliberate. Injecting it would make /api/read/* readable by anyone who found the
    # distribution, dropping the InternalSecretFilter perimeter. Keeping it in the key
    # means a caller without the secret computes a different key, misses the cache, and
    # is rejected by the origin with 403 -- the perimeter survives being fronted by a
    # CDN. There is only one valid secret in steady state, so this is one cache
    # partition, not fragmentation. (A rotation window with secret.next set is briefly
    # two, which is harmless.)
    headers_config {
      header_behavior = "whitelist"

      headers {
        items = ["X-Internal-Secret"]
      }
    }

    # Cookies are excluded from the cache key but still forwarded to the origin by the
    # origin request policy below. Safe only because every response whose body varies on
    # the gallery_access_<slug> cookie is served no-store -- CloudFront never stores it,
    # so there is no cookie-blind cache entry to leak. That coupling is why the origin
    # Cache-Control work must ship before this.
    cookies_config {
      cookie_behavior = "none"
    }
  }
}

resource "aws_cloudfront_origin_request_policy" "api_read" {
  count = var.api_origin_domain == "" ? 0 : 1

  name    = "${var.project_name}-api-read"
  comment = "Forwards cookies and query strings to the API origin."

  # The gallery_access_<slug> cookie MUST reach the origin or isGalleryAccessAuthorized
  # always fails and a password-protected gallery can never unlock through the CDN.
  cookies_config {
    cookie_behavior = "all"
  }

  query_strings_config {
    query_string_behavior = "all"
  }

  # "none" here is not "no headers": headers in the cache key are always forwarded to the
  # origin, so X-Internal-Secret still arrives.
  headers_config {
    header_behavior = "none"
  }
}

resource "aws_cloudfront_distribution" "portfolio" {
  enabled         = true
  comment         = ""
  is_ipv6_enabled = true
  price_class     = "PriceClass_100"

  origin {
    domain_name              = aws_s3_bucket.portfolio.bucket_regional_domain_name
    origin_id                = "${var.s3_bucket_name}.s3.us-west-2.amazonaws.com"
    origin_access_control_id = aws_cloudfront_origin_access_control.portfolio.id
  }

  # Caddy terminates TLS on EC2, so this is a custom (non-S3) origin reached over HTTPS.
  dynamic "origin" {
    for_each = var.api_origin_domain == "" ? [] : [var.api_origin_domain]

    content {
      domain_name = origin.value
      origin_id   = local.api_origin_id

      custom_origin_config {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "https-only"
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "${var.s3_bucket_name}.s3.us-west-2.amazonaws.com"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    # Managed cache/request/response policies (created via AWS console)
    cache_policy_id            = "0683378f-5712-40d6-80dd-7a89483aa8d5"
    origin_request_policy_id   = "88a5eaf4-2fd4-4709-b370-b4c650ea3fcf"
    response_headers_policy_id = "c23c127a-d30c-462b-8050-95876e6ab5b9"

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  dynamic "ordered_cache_behavior" {
    for_each = var.api_origin_domain == "" ? [] : [1]

    content {
      path_pattern     = "/api/read/*"
      target_origin_id = local.api_origin_id

      # The read surface is not read-only by HTTP method: POST /collections/{slug}/access
      # unlocks a client gallery, and the user follows/saves/selects/ratings routes POST
      # and DELETE. Restricting this to GET/HEAD would 405 them at the edge. Only GET and
      # HEAD are ever cached; CloudFront never caches the rest.
      allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods  = ["GET", "HEAD"]

      # https-only rather than redirect-to-https: a 301 on a POST would drop the body.
      viewer_protocol_policy = "https-only"
      compress               = true

      cache_policy_id          = aws_cloudfront_cache_policy.api_read[0].id
      origin_request_policy_id = aws_cloudfront_origin_request_policy.api_read[0].id
    }
  }

  # Catch-all for the rest of the API. MUST stay declared after the /api/read/* block:
  # CloudFront evaluates ordered behaviors in order and takes the first match, so the
  # specific read pattern has to win before this one.
  #
  # Without this, the BFF cannot point a single API_URL at CloudFront. Its proxy is one
  # catch-all route that forwards every path -- /api/auth/* (session cookies, ~12 call
  # sites), /api/admin/*, /api/public/* -- through the same base URL. Those paths would
  # otherwise fall to default_cache_behavior, whose origin is the S3 IMAGE BUCKET, and
  # auth would break outright.
  #
  # Nothing here is cached: these are writes, per-user reads, and admin traffic. The
  # backend also marks them no-store, so this is belt-and-braces.
  dynamic "ordered_cache_behavior" {
    for_each = var.api_origin_domain == "" ? [] : [1]

    content {
      path_pattern     = "/api/*"
      target_origin_id = local.api_origin_id

      allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods  = ["GET", "HEAD"]

      viewer_protocol_policy = "https-only"
      compress               = true

      cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
      origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  # Prevent accidental destruction of the distribution -- recreation takes ~15 min.
  lifecycle {
    prevent_destroy = true
  }
}
