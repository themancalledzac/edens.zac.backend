# =============================================================================
# Portfolio Backend -- Terraform Configuration
# =============================================================================
# This configuration codifies EXISTING AWS infrastructure. Resources were
# originally created manually and are being imported into Terraform state.
#
# To import existing resources, run the import blocks below or use:
#   terraform plan -generate-config-out=generated.tf
#
# Prerequisites:
#   1. Copy terraform.tfvars.example to terraform.tfvars
#   2. Fill in real values (bucket name, key pair, SSH CIDR, etc.)
#   3. Run: terraform init
#   4. Import existing resources (see import comments in each file)
#   5. Run: terraform plan  (should show no changes if imports match)
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Local backend for now -- migrate to S3 backend later if desired
  backend "local" {
    path = "terraform.tfstate"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
