variable "cluster_name" {
    description = "EKS Cluster name" 
    type = string
}

variable "oidc_provider" {
    description = "OIDC provider hostname only - used as the IAM condition variable"
    type        = string
}

variable "oidc_provider_arn" {
    description = "ARN of the OIDC provider for the EKS cluster - used in IRSA trust policy"
    type        = string
}

variable "vpc_id" {
    description = "VPC ID"
    type        = string
}

variable "region" {
    description = "AWS region"
    type        = string
}