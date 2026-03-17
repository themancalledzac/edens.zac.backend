# =============================================================================
# IAM -- Application User for S3 Access
# =============================================================================
# Existing IAM user used by the backend application for S3 operations.
# The user has two managed policies attached:
#   1. portfolio-s3-access-policy -- grants S3 CRUD on edens.zac.portfolio + SSM read for CDK bootstrap
#   2. AmplifyBackendDeployFullAccess -- AWS managed policy, parked for future cleanup
#
# The managed policies already exist in AWS and are NOT managed by this Terraform config.
# We only manage the policy attachments here.
#
# Import:
#   terraform import aws_iam_user.portfolio_s3_uploader portfolio-s3-uploader
#   terraform import aws_iam_user_policy_attachment.s3_access portfolio-s3-uploader/arn:aws:iam::100893805555:policy/portfolio-s3-access-policy
#   terraform import aws_iam_user_policy_attachment.amplify_access portfolio-s3-uploader/arn:aws:iam::aws:policy/service-role/AmplifyBackendDeployFullAccess
#
# NOTE: Access keys are managed outside Terraform. Do NOT put secrets in .tf files.
# =============================================================================

resource "aws_iam_user" "portfolio_s3_uploader" {
  # Hardcoded to match the actual IAM user name -- cannot be renamed without recreation.
  name = "portfolio-s3-uploader"

  tags = {
    AKIARO7N5JPZ5COSNUPU = "Application running outside AWS"
  }
}

# Managed policy granting S3 CRUD on the portfolio bucket + SSM read for CDK bootstrap.
# The policy document itself is managed outside Terraform (already exists in AWS).
resource "aws_iam_user_policy_attachment" "s3_access" {
  user       = aws_iam_user.portfolio_s3_uploader.name
  policy_arn = "arn:aws:iam::100893805555:policy/portfolio-s3-access-policy"
}

# AmplifyBackendDeployFullAccess is an AWS-managed policy parked on this user.
# TODO: evaluate whether this is still needed and remove if not.
resource "aws_iam_user_policy_attachment" "amplify_access" {
  user       = aws_iam_user.portfolio_s3_uploader.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmplifyBackendDeployFullAccess"
}
