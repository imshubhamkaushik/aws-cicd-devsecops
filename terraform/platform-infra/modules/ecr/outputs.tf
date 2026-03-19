output "registry_url" {
  description = "ECR registry base URL (account.dkr.ecr.region.amazonaws.com)"
  value       = split("/", aws_ecr_repository.repos[keys(aws_ecr_repository.repos)[0]].repository_url)[0]
}

output "repository_urls" {
  description = "Map of repository name to full ECR URL"
  value       = { for name, repo in aws_ecr_repository.repos : name => repo.repository_url }
}