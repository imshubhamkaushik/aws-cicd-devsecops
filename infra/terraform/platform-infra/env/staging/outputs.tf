output "cluster_name" {
  description = "EKS cluster name — use in: aws eks update-kubeconfig --name <value>"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  description = "RDS Postgres endpoint (staging)"
  value       = module.rds.rds_endpoint
}

output "ecr_registry" {
  description = "ECR registry base URL — same registry used by dev and staging"
  value       = "Shared with dev — see dev env outputs or ECR console"
}
