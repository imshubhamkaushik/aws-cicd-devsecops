# Security groups
# EKS Nodes security group - node-to-node communication
resource "aws_security_group" "eks_nodes" {
  name   = "eks-nodes-sg"
  description = "Security group to allow node-to-node communication for all nodes within the node group in the cluster"
  vpc_id = var.vpc_id

  ingress {
    description = "Allow nodes to communicate with each other"
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# RDS security group - restricts Postgres access to VPC-internal access only
resource "aws_security_group" "rds" {
  name   = "rds-sg"
  description = "Security group for RDS"
  vpc_id = var.vpc_id

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

# # ALB security group
# resource "aws_security_group" "alb" {
#   name   = "alb-sg"
#   description = "Security group for Application Load Balancer to Allow inbound HTTP and HTTPS traffic from internet"
#   vpc_id = var.vpc_id

#   ingress {
#     description = "Allow HTTP from internet"
#     from_port   = 80
#     to_port     = 80
#     protocol    = "tcp"
#     cidr_blocks = ["0.0.0.0/0"]
#   }

#   ingress {
#     description = "Allow HTTPS from internet"
#     from_port   = 443
#     to_port     = 443
#     protocol    = "tcp"
#     cidr_blocks = ["0.0.0.0/0"]
#   }

#   egress {
#     description = "Allow all outbound"
#     from_port   = 0
#     to_port     = 0
#     protocol    = "-1"
#     cidr_blocks = ["0.0.0.0/0"]
#   }
# }