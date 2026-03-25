output "cluster_name" {
  description = "EKS cluster name — use in: aws eks update-kubeconfig --name <value>"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  description = "RDS Postgres endpoint — use as SPRING_DATASOURCE_URL host"
  value       = module.rds.rds_endpoint
}

output "ecr_registry" {
  description = "ECR registry base URL — used in Jenkinsfile as ECR_REGISTRY"
  value       = module.ecr.registry_url
}
