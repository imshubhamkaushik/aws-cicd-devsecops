# Security groups

# RDS security group - restricts Postgres access to VPC-internal access only
# EKS node-to-node traffic is handled automatically by the EKS-managed cluster
# security group. ALB controller manages its own SGs when creating load balancers
# via Ingress annotations. Neither needs a manually created SG here.

# -----------------------------------------------------------------------
# IMPORTANT: AWS provider v6 does not support inline ingress/egress blocks
# inside aws_security_group combined with aws_security_group_rule resources
# for the same SG. Mixing the two causes perpetual plan drift.
#
# FIX: Removed inline ingress/egress blocks from aws_security_group.rds.
# All rules are now managed exclusively via standalone
# aws_security_group_rule resources — consistent with bootstrap-infra.
# -----------------------------------------------------------------------

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Security group for RDS"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.project_name}-rds-sg"
  }
}

resource "aws_security_group_rule" "rds_ingress_postgres" {
  description       = "Postgres access from VPC (EKS pods, Jenkins, EC2)"
  type              = "ingress"
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  cidr_blocks       = [var.vpc_cidr]
  security_group_id = aws_security_group.rds.id
}

resource "aws_security_group_rule" "rds_egress_all" {
  description       = "Allow all outbound"
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.rds.id
}

resource "aws_security_group_rule" "jenkins_to_eks_api" {
  description              = "Allow Jenkins to reach the EKS private API endpoint on port 443"
  type                     = "ingress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  source_security_group_id = var.jenkins_sg_id
  security_group_id        = var.eks_cluster_sg_id
}