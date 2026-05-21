output "cluster_name" {
  description = "EKS cluster name — use in: aws eks update-kubeconfig --name <value>"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  description = "RDS Postgres endpoint address — use as SPRING_DATASOURCE_URL host"
  value       = module.rds.rds_address # hostname only, no port — use as database.host in Helm values
}

output "ecr_registry" {
  description = "ECR registry base URL — used in Jenkinsfile as ECR_REGISTRY"
  value       = module.ecr.registry_url
}

output "alb_role_arn" {
  description = "ARN of the IAM role for the AWS Load Balancer Controller — use in ALB Helm chart values for IRSA"
  value       = module.alb.alb_role_arn
}
