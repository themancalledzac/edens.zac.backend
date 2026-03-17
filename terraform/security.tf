# =============================================================================
# Security Group -- Portfolio Backend
# =============================================================================
# Existing security group controlling access to the EC2 instance.
# Port 5432 is intentionally NOT exposed -- PostgreSQL runs inside Docker
# and is only accessible from within the instance.
#
# The SG name is hardcoded as "launch-wizard-1" because AWS does not allow
# renaming security groups after creation.
#
# Import:
#   terraform import aws_security_group.portfolio sg-040d794c6cc074a69
#
#   terraform import aws_vpc_security_group_ingress_rule.ssh sgr-0130ee2e54f67c05e
#   terraform import aws_vpc_security_group_ingress_rule.backend_api sgr-01a9833be3517e60d
#   terraform import aws_vpc_security_group_egress_rule.all_outbound sgr-0335b654bc1aca144
# =============================================================================

resource "aws_security_group" "portfolio" {
  name        = "launch-wizard-1"
  description = "launch-wizard-1 created 2025-03-02T08:51:08.225Z"
  vpc_id      = var.vpc_id
}

# --- Ingress Rules ---

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.portfolio.id
  description       = "SSH access from admin IP"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.ssh_allowed_cidr
}

resource "aws_vpc_security_group_ingress_rule" "backend_api" {
  security_group_id = aws_security_group.portfolio.id
  # Intentionally public: Amplify (managed service, outside VPC) reaches EC2 via public IP.
  # InternalSecretFilter rejects unauthenticated requests (403 without X-Internal-Secret header).
  # Can close this port once Caddy handles HTTPS on 443 instead.
  description = "Backend API -- Amplify BFF proxy. InternalSecretFilter rejects unauthenticated requests."
  ip_protocol = "tcp"
  from_port   = 8080
  to_port     = 8080
  cidr_ipv4   = "0.0.0.0/0"
}

# --- Egress Rules ---

resource "aws_vpc_security_group_egress_rule" "all_outbound" {
  security_group_id = aws_security_group.portfolio.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
