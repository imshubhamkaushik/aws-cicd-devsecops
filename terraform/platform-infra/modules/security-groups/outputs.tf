output "eks_nodes_sg" {
    description = "EKS Node Security Group"
    value = aws_security_group.eks_nodes.id 
}

output "rds_sg" { 
    description = "RDS Security Group"
    value = aws_security_group.rds.id 
}

# output "alb_sg" { 
#     description = "ALB Security Group"
#     value = aws_security_group.alb.id 
# }