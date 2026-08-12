# =============================================================================
# Outputs
# =============================================================================
# Key values exported after apply. Useful for reference and scripting.
# =============================================================================

output "ec2_public_ip" {
  description = "Public IP address of the portfolio EC2 instance"
  value       = aws_instance.portfolio.public_ip
}

output "s3_bucket_name" {
  description = "Name of the portfolio S3 bucket"
  value       = aws_s3_bucket.portfolio.id
}

output "s3_bucket_arn" {
  description = "ARN of the portfolio S3 bucket"
  value       = aws_s3_bucket.portfolio.arn
}

output "cloudfront_domain_name" {
  description = "Domain name of the CloudFront distribution"
  value       = aws_cloudfront_distribution.portfolio.domain_name
}

output "cloudfront_distribution_id" {
  description = "ID of the CloudFront distribution"
  value       = aws_cloudfront_distribution.portfolio.id
}

output "api_read_base_url" {
  description = <<-EOT
    Base URL the frontend BFF should call for /api/read/* once the API origin is enabled.
    Null while api_origin_domain is unset. The BFF must keep sending X-Internal-Secret --
    CloudFront does not inject it, and it is part of the cache key.
  EOT
  value = (
    var.api_origin_domain == ""
    ? null
    : "https://${aws_cloudfront_distribution.portfolio.domain_name}/api/read"
  )
}

output "security_group_id" {
  description = "ID of the portfolio security group"
  value       = aws_security_group.portfolio.id
}
