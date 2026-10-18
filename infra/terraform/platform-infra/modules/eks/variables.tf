variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "cluster_version" {
  description = "Version of the EKS cluster"
  type        = string
  default     = "1.35"
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

variable "disk_size" {
  description = "Root volume size (GB) for worker nodes"
  type        = number
  default     = 30
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

variable "jenkins_public_ip" {
  description = "Public IP of the Jenkins server to allowlist on the EKS API"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "console_iam_arn" {
  description = "IAM ARN granted EKS cluster-admin for console access. Passed as data.aws_caller_identity.current.arn from the calling module — automatically the identity running terraform apply."
  type        = string
}

variable "operator_cidrs" {
  description = <<-EOT
    Operator IP CIDRs (for example ["203.0.113.5/32"]) read from the
    bootstrap-infra remote state output allowed_cidrs. They are added to the
    EKS cluster's public_access_cidrs next to the Jenkins IP so the operator's
    kubectl can reach the API. Adding an entry updates the cluster in place.
    EKS accepts at most 40 CIDRs in total, so prune old entries with
    scripts/python/aws/allowlist.py.
  EOT
  type        = list(string)
  default     = []
}

variable "permissions_boundary_arn" {
  description = <<-EOT
    ARN of the IAM permissions boundary (created once in bootstrap-infra)
    that must be attached to every IAM role this module creates.
    Jenkins's own IAM policy requires this boundary on any role it creates
    via iam:CreateRole — omitting it here means terraform apply fails with
    AccessDenied, by design. See bootstrap-infra/iam.tf for the full
    rationale (this closes a PassRole/CreateRole privilege-escalation path).
  EOT
  type        = string
}