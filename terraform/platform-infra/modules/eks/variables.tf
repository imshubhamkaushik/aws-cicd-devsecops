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
