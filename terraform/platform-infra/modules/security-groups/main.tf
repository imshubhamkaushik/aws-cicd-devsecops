# Security groups

# RDS security group - restricts Postgres access to VPC-internal access only
# EKS node-to-node traffic is handled automatically by the EKS-managed cluster
# security group. ALB controller manages its own SGs when creating load balancers
# via Ingress annotations. Neither needs a manually created SG here.

# Rules are standalone aws_security_group_rule resources, never inline blocks
# (mixing both causes perpetual plan drift), consistent with bootstrap-infra.

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Security group for RDS"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.project_name}-rds-sg"
  }
}

resource "aws_security_group_rule" "rds_ingress_eks_nodes" {
  description              = "Postgres access from EKS nodes/pods (shares the cluster's primary SG)"
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = var.eks_cluster_sg_id
  security_group_id        = aws_security_group.rds.id
}

resource "aws_security_group_rule" "rds_ingress_jenkins" {
  description              = "Postgres access from Jenkins (migrations / debugging)"
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = var.jenkins_sg_id
  security_group_id        = aws_security_group.rds.id
}

# No egress rule: RDS does not initiate outbound connections.

resource "aws_security_group_rule" "jenkins_to_eks_api" {
  description              = "Allow Jenkins to reach the EKS private API endpoint on port 443"
  type                     = "ingress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  source_security_group_id = var.jenkins_sg_id
  security_group_id        = var.eks_cluster_sg_id
}