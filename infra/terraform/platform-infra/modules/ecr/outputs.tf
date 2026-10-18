output "registry_url" {
  description = "ECR registry base URL (account.dkr.ecr.region.amazonaws.com)"
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.region}.amazonaws.com"
}

output "repository_urls" {
  description = "Map of repository name to full ECR URL"
  value       = { for name, repo in aws_ecr_repository.repos : name => repo.repository_url }
}