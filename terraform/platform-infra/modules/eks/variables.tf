variable "cluster_name" {
    description = "Name of the EKS cluster"
    type = string
}

variable "cluster_version" {
    description = "Version of the EKS cluster"
    type = string
    default = "1.32"
}

variable "private_subnets" {
    description = "List of private subnet IDs for EKS cluster"
    type = list(string)
}

variable "instance_type" {
    description = "EC2 instance types of Worker nodes"
    type = list(string)
    default = [ "t3.medium" ]
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
