variable "cluster_name" {
  description = "EKS cluster name — used to namespace IAM resource names"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the EKS OIDC provider — used in the IRSA trust policy"
  type        = string
}

variable "oidc_provider" {
  description = "OIDC provider hostname only (no arn: prefix) — used as the IAM condition variable"
  type        = string
}

variable "region" {
  description = "AWS region — passed to the ClusterSecretStore so ESO knows which region to call"
  type        = string
}

variable "permissions_boundary_arn" {
  description = "ARN of the IAM permissions boundary that must be attached to the role this module creates. See modules/eks/variables.tf for the full rationale."
  type        = string
}