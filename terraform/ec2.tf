# =============================================================================
# EC2 Instance -- Portfolio Backend
# =============================================================================
# Existing t3.micro running Amazon Linux 2023 with Docker + PostgreSQL.
# PostgreSQL runs as a Docker container on this instance (not RDS).
#
# Import:
#   terraform import aws_instance.portfolio i-0a125f53892b6f138
# =============================================================================

resource "aws_instance" "portfolio" {
  ami                    = var.ec2_ami_id
  instance_type          = var.instance_type
  key_name               = var.key_pair_name
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.portfolio.id]

  root_block_device {
    volume_size = 8
    volume_type = "gp3"
    encrypted   = false
  }

  # NOTE: If the live instance has an IAM instance profile attached, add it here:
  # iam_instance_profile = aws_iam_instance_profile.portfolio.name
  # Currently the app uses long-lived IAM user keys (defined in iam.tf) for S3 access.

  tags = {
    Name = "portfolio-backend"
  }

  # Prevent accidental destruction of the running instance.
  # ignore_changes on ami prevents perpetual diff if AMI ID drifts.
  # ignore_changes on user_data because user_data was not set at launch time.
  lifecycle {
    prevent_destroy = true
    ignore_changes  = [ami, user_data]
  }
}
