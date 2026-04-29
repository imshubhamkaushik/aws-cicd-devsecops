variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "cluster_version" {
  description = "Version of the EKS cluster"
  type        = string
  default     = "1.32"
}

output "cluster_sg_id" {
  description = "EKS cluster security group ID (AWS-managed) — passed to security-groups module for the Jenkins ingress rule"
  value       = aws_eks_cluster.cluster.vpc_config[0].cluster_security_group_id
}

variable "private_subnets" {
  description = "List of private subnet IDs for EKS cluster"
  type        = list(string)
}

variable "instance_type" {
  description = "EC2 instance types of Worker nodes"
  type        = list(string)
  default     = ["c7i-flex.large"]
}

variable "min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 3
}

variable "desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "kms_key_arn" {
  description = "KMS key ARN for EKS secrets encryption"
  type        = string
}

variable "jenkins_role_arn" {
  description = "Jenkins role ARN"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}