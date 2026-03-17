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

output "security_group_id" {
  description = "ID of the portfolio security group"
  value       = aws_security_group.portfolio.id
}
