# Security groups

# RDS security group - restricts Postgres access to VPC-internal access only
# EKS node-to-node traffic is handled automatically by the EKS-managed cluster
# security group. ALB controller manages its own SGs when creating load balancers
# via Ingress annotations. Neither needs a manually created SG here.
resource "aws_security_group" "rds" {
  name        = "rds-sg"
  description = "Security group for RDS"
  vpc_id      = var.vpc_id

  ingress {
    description = "Postgres access from VPC (EKS pods, Jenkins, EC2)"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
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
