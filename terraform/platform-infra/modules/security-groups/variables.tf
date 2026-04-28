variable "vpc_id" {
  description = "VPC ID to create all security groups in."
  type        = string
}
variable "vpc_cidr" {
  description = "VPC CIDR block — used to restrict internal-only rules to VPC traffic"
  type        = string
}

variable "jenkins_sg_id" {
  description = "Jenkins SG ID — source for the EKS API ingress rule"
  type        = string
}

variable "eks_cluster_sg_id" {
  description = "EKS cluster SG ID (AWS-managed) — target for the Jenkins ingress rule"
  type        = string
}