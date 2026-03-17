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
