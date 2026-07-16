# =============================================================================
# S3 Bucket -- Portfolio Images and DB Backups
# =============================================================================
# Existing S3 bucket storing:
#   - Original and web-optimized portfolio images
#   - PostgreSQL database backups under db-backups/ prefix
#
# Public access block is currently disabled (all false) -- images are served
# via CloudFront with OAC. The bucket policy restricts access to CloudFront only.
# TODO: Enable public access block after confirming no impact on CloudFront OAC.
#
# Import:
#   terraform import aws_s3_bucket.portfolio edens.zac.portfolio
#   terraform import aws_s3_bucket_public_access_block.portfolio edens.zac.portfolio
# =============================================================================

resource "aws_s3_bucket" "portfolio" {
  bucket = var.s3_bucket_name

  # Prevent accidental deletion of the bucket with all portfolio data
  lifecycle {
    prevent_destroy = true
  }
}

# NOTE: Versioning is not enabled on this bucket. If needed in the future,
# add an aws_s3_bucket_versioning resource here.

# Lifecycle: expire the ephemeral download ZIPs the backend builds under downloads-tmp/.
# The download endpoints stream a collection ZIP into S3 and hand the browser a short-lived
# presigned URL (to bypass the Amplify 5.72 MB response cap); the object itself is disposable
# once the client has downloaded it. Expire after 1 day and reap any parts left by an
# interrupted multipart upload.
#
# NOTE: apply required -- `terraform apply` this after merge. If a lifecycle configuration is
# ever added to this bucket outside terraform, import it first to avoid a conflict.
resource "aws_s3_bucket_lifecycle_configuration" "portfolio" {
  bucket = aws_s3_bucket.portfolio.id

  rule {
    id     = "expire-ephemeral-download-zips"
    status = "Enabled"

    filter {
      prefix = "downloads-tmp/"
    }

    expiration {
      days = 1
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_public_access_block" "portfolio" {
  bucket = aws_s3_bucket.portfolio.id

  # Safe to enable -- images are served via CloudFront OAC, not direct S3 access.
  # The bucket policy restricts access to the CloudFront service principal only.
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
