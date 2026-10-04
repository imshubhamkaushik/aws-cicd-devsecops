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

# The passwords YOU chose (RDS master, RabbitMQ, app admin, Grafana) are not outputs — they live in
# Secrets Manager. This prints the commands to review or change them (passwords are never shown).
output "operator_credentials_commands" {
  description = "How to see which operator credentials are set, or change one"
  value = {
    secret = "${local.env_prefix}/operator-credentials"
    show   = "python scripts/python/credentials.py --env dev --show"
    change = "python scripts/python/credentials.py --env dev --only <key>"
  }
}
