# =============================================================================
# Variables
# =============================================================================
# All configurable values for the portfolio infrastructure.
# Sensitive or environment-specific values belong in terraform.tfvars (not committed).
# =============================================================================

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-west-2"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"
}

variable "vpc_id" {
  description = "VPC ID where the EC2 instance and security group live"
  type        = string
}

variable "subnet_id" {
  description = "Subnet ID to launch the EC2 instance into"
  type        = string
}

variable "ec2_ami_id" {
  description = "AMI ID for the EC2 instance -- get the current value from the AWS console (EC2 > Instances)"
  type        = string
}

variable "key_pair_name" {
  description = "Name of the existing EC2 key pair for SSH access"
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "Your IP in CIDR notation for SSH access (e.g., 203.0.113.10/32)"
  type        = string
}

variable "s3_bucket_name" {
  description = "Name of the existing S3 bucket for portfolio images and DB backups"
  type        = string
}

variable "project_name" {
  description = "Project name used for tagging and naming resources"
  type        = string
  default     = "portfolio"
}

variable "environment" {
  description = "Environment name (e.g., production, staging)"
  type        = string
  default     = "production"
}
