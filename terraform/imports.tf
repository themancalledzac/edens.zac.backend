# =============================================================================
# Import Blocks -- Terraform 1.5+ declarative imports
# =============================================================================
# These blocks tell Terraform which existing AWS resources map to which
# resource addresses in this configuration.
#
# Usage:
#   terraform init
#   terraform plan   # Terraform will read the live resources and show any diffs
#   terraform apply  # Writes state only -- no infrastructure is created/destroyed
#
# After a successful import, these blocks can remain in place (they are idempotent
# once the resource is in state) or be removed.
#
# NOTE: aws_s3_bucket_versioning and aws_s3_bucket_lifecycle_configuration
# are intentionally NOT listed here. We need to verify whether those
# configurations exist in AWS before importing them to avoid a plan diff.
# =============================================================================

import {
  to = aws_instance.portfolio
  id = "i-0a125f53892b6f138"
}

import {
  to = aws_security_group.portfolio
  id = "sg-040d794c6cc074a69"
}

import {
  to = aws_vpc_security_group_ingress_rule.ssh
  id = "sgr-0130ee2e54f67c05e"
}

import {
  to = aws_vpc_security_group_ingress_rule.backend_api
  id = "sgr-01a9833be3517e60d"
}

import {
  to = aws_vpc_security_group_egress_rule.all_outbound
  id = "sgr-0335b654bc1aca144"
}

import {
  to = aws_s3_bucket.portfolio
  id = "edens.zac.portfolio"
}

import {
  to = aws_s3_bucket_public_access_block.portfolio
  id = "edens.zac.portfolio"
}

import {
  to = aws_cloudfront_origin_access_control.portfolio
  id = "E12HK3694LNYG0"
}

import {
  to = aws_cloudfront_distribution.portfolio
  id = "E2SR03MLB2ZFMR"
}

import {
  to = aws_s3_bucket_policy.cloudfront_access
  id = "edens.zac.portfolio"
}

import {
  to = aws_iam_user.portfolio_s3_uploader
  id = "portfolio-s3-uploader"
}

import {
  to = aws_iam_user_policy_attachment.s3_access
  id = "portfolio-s3-uploader/arn:aws:iam::100893805555:policy/portfolio-s3-access-policy"
}

import {
  to = aws_iam_user_policy_attachment.amplify_access
  id = "portfolio-s3-uploader/arn:aws:iam::aws:policy/service-role/AmplifyBackendDeployFullAccess"
}
