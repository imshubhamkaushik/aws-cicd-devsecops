variable "cluster_name" {
  description = "EKS Cluster name"
  type        = string
}

variable "oidc_provider" {
  description = "OIDC provider hostname only - used as the IAM condition variable"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the OIDC provider for the EKS cluster - used in IRSA trust policy"
  type        = string
}

variable "permissions_boundary_arn" {
  description = "ARN of the IAM permissions boundary that must be attached to the role this module creates. See modules/eks/variables.tf for the full rationale."
  type        = string
}