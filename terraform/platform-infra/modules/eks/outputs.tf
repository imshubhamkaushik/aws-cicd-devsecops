output "cluster_name" {
  description = "EKS Cluster Name"
  value       = aws_eks_cluster.cluster.name
}

output "cluster_endpoint" {
  description = "EKS Cluster Endpoint"
  value       = aws_eks_cluster.cluster.endpoint
}

output "cluster_certificate" {
  description = "EKS Cluster Certificate"
  value       = aws_eks_cluster.cluster.certificate_authority[0].data
  sensitive   = true
}

output "oidc_provider_arn" {
  description = "OIDC Provider ARN - used to create IRSA trust policies"
  value       = aws_iam_openid_connect_provider.oidc_provider.arn
}

output "cluster_sg_id" {
  description = "EKS cluster security group ID (AWS-managed) — passed to security-groups module for the Jenkins ingress rule"
  value       = aws_eks_cluster.cluster.vpc_config[0].cluster_security_group_id
}

output "node_role_arn" {
  description = "ARN of the EKS node IAM role — used in aws-auth ConfigMap to allow nodes to join the cluster"
  value       = aws_iam_role.node_role.arn
}