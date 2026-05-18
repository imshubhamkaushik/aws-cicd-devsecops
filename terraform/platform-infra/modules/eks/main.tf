# Auto-detect the public IP of whoever runs terraform apply.
# Same pattern used in bootstrap-infra/security-groups.tf.
# This locks EKS public API access to your machine only — never 0.0.0.0/0
data "http" "my_ip" {
  url = "https://checkip.amazonaws.com"
}

locals {
  my_ip_cidr = "${chomp(data.http.my_ip.response_body)}/32"
}

# IAM Role for cluster
data "aws_iam_policy_document" "cluster_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "cluster_role" {
  name               = "${var.cluster_name}-cluster-role"
  assume_role_policy = data.aws_iam_policy_document.cluster_assume_role.json
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role_policy_attachment" "vpc_controller" {
  role       = aws_iam_role.cluster_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
}

# EKS Cluster
resource "aws_eks_cluster" "cluster" {
  name     = var.cluster_name
  version  = var.cluster_version
  role_arn = aws_iam_role.cluster_role.arn

  vpc_config {
    subnet_ids              = var.private_subnets
    endpoint_private_access = true
    # DEV NOTE: public access is on so you can run kubectl from your laptop.
    # For production set this to false and access only from within the VPC.
    endpoint_public_access = true # for production, set to false
    public_access_cidrs = [
      "${var.jenkins_public_ip}/32",
    local.my_ip_cidr] # auto-locked to your IP at apply time | use this if endpoint_public_access = true
  }

  access_config {
    authentication_mode                         = "API_AND_CONFIG_MAP"
    bootstrap_cluster_creator_admin_permissions = true
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator"]

  encryption_config {
    resources = ["secrets"]

    provider {
      key_arn = var.kms_key_arn
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
    aws_iam_role_policy_attachment.vpc_controller
  ]
}

# OIDC Provider — required for IRSA (IAM Roles for Service Accounts)
data "tls_certificate" "oidc_thumbprint" {
  url = aws_eks_cluster.cluster.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "oidc_provider" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc_thumbprint.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.cluster.identity[0].oidc[0].issuer
}

# IAM Role for EKS worker nodes
data "aws_iam_policy_document" "node_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "node_role" {
  name               = "${var.cluster_name}-node-role"
  assume_role_policy = data.aws_iam_policy_document.node_assume_role.json
}

# Attach Policies
resource "aws_iam_role_policy_attachment" "node" {
  role       = aws_iam_role.node_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "cni" {
  role       = aws_iam_role.node_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "ecr" {
  role       = aws_iam_role.node_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# Node Group
resource "aws_eks_node_group" "node_group" {
  cluster_name  = aws_eks_cluster.cluster.name
  node_role_arn = aws_iam_role.node_role.arn
  subnet_ids    = var.private_subnets

  instance_types = var.instance_type
  ami_type       = "AL2023_x86_64_STANDARD"
  capacity_type  = "ON_DEMAND"
  disk_size      = 20

  node_group_name = "${var.cluster_name}-node-group"

  scaling_config {
    desired_size = var.desired_size
    max_size     = var.max_size
    min_size     = var.min_size
  }

  update_config {
    max_unavailable = 1
  }

  labels = {
    role = "worker-node"
  }

  depends_on = [
    aws_iam_role_policy_attachment.node,
    aws_iam_role_policy_attachment.cni,
    aws_iam_role_policy_attachment.ecr,
  ]

}

# EKS Add-ons — pinned versions so upgrades are deliberate, not silent.
# To find latest versions: aws eks describe-addon-versions --kubernetes-version 1.35
# VPC CNI
resource "aws_eks_addon" "vpc_cni" {
  cluster_name                = aws_eks_cluster.cluster.name
  addon_name                  = "vpc-cni"
  addon_version               = "v1.21.1-eksbuild.8"
  resolve_conflicts_on_update = "OVERWRITE"

  depends_on = [aws_eks_node_group.node_group]
}

# CoreDNS
resource "aws_eks_addon" "coredns" {
  cluster_name                = aws_eks_cluster.cluster.name
  addon_name                  = "coredns"
  addon_version               = "v1.14.2-eksbuild.4"
  resolve_conflicts_on_update = "OVERWRITE"

  depends_on = [aws_eks_node_group.node_group]
}

# kube-proxy
resource "aws_eks_addon" "kube_proxy" {
  cluster_name                = aws_eks_cluster.cluster.name
  addon_name                  = "kube-proxy"
  addon_version               = "v1.35.3-eksbuild.5"
  resolve_conflicts_on_update = "OVERWRITE"

  depends_on = [aws_eks_node_group.node_group]
}

# EBS CSI Driver
data "aws_iam_policy_document" "ebs_csi_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.oidc_provider.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${aws_iam_openid_connect_provider.oidc_provider.url}:sub"
      values   = ["system:serviceaccount:kube-system:ebs-csi-controller-sa"]
    }
    condition {
      test     = "StringEquals"
      variable = "${aws_iam_openid_connect_provider.oidc_provider.url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ebs_csi" {
  name               = "${var.cluster_name}-ebs-csi-role"
  assume_role_policy = data.aws_iam_policy_document.ebs_csi_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.ebs_csi.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

resource "aws_eks_addon" "ebs_csi" {
  cluster_name                = aws_eks_cluster.cluster.name
  addon_name                  = "aws-ebs-csi-driver"
  service_account_role_arn    = aws_iam_role.ebs_csi.arn
  resolve_conflicts_on_update = "OVERWRITE"

  depends_on = [
    aws_eks_node_group.node_group,
    aws_iam_role_policy_attachment.ebs_csi
  ]
}

# gp3 StorageClass — used by Prometheus and Grafana PVC requests.
# WaitForFirstConsumer ensures the volume is created in the same AZ as the pod.
# resource "kubernetes_storage_class_v1" "gp3" {
#   metadata {
#     name = "gp3-sc"
#     annotations = {
#       # Not set as default to avoid silently provisioning volumes for other workloads
#       "storageclass.kubernetes.io/is-default-class" = "false"
#     }
#   }

#   storage_provisioner    = "ebs.csi.aws.com"
#   reclaim_policy         = "Retain"
#   volume_binding_mode    = "WaitForFirstConsumer"
#   allow_volume_expansion = true

#   parameters = {
#     type = "gp3"
#   }

#   depends_on = [aws_eks_addon.ebs_csi]
# }

# NOTE: kubernetes_storage_class_v1.gp3 was intentionally moved to env/dev/main.tf.
#
# Reason: this resource uses the kubernetes provider, which is configured with
# local.cluster_endpoint = module.eks.cluster_endpoint. During 
# (terraform apply -target=module.eks), the endpoint is not yet in provider
# config — it was "(known after apply)" at plan time, so Terraform initialises
# the kubernetes provider pointing at localhost:80. The StorageClass apply then
# fails immediately with "dial tcp 127.0.0.1:80: connection refused".
#
# Moving it to the root module means it only runs in (full apply), by
# which time module.eks is already in state, the endpoint is known, and the
# kubernetes provider connects to the real cluster.

# Allow your Jenkins EC2 instance (and terminal) to run kubectl commands
resource "aws_eks_access_entry" "jenkins_admin" {
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = var.jenkins_role_arn
  type          = "STANDARD"

  depends_on = [
    aws_eks_cluster.cluster,
    aws_eks_node_group.node_group
  ]
}

resource "aws_eks_access_policy_association" "jenkins_admin_policy" {
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = var.jenkins_role_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [
    aws_eks_cluster.cluster,
    aws_eks_node_group.node_group,
    aws_eks_access_entry.jenkins_admin
  ]
}

# resource "kubernetes_config_map_v1" "aws_auth" {
#   metadata {
#     name      = "aws-auth"
#     namespace = "kube-system"
#   }

#   data = {
#     mapRoles = <<EOF
# - rolearn: ${aws_iam_role.node_role.arn}
#   username: system:node:{{EC2PrivateDNSName}}
#   groups:
#     - system:bootstrappers
#     - system:nodes
# EOF
#   }

#   depends_on = [aws_eks_node_group.node_group]
# }

resource "null_resource" "aws_auth" {
  triggers = {
    # Re-apply the aws-auth ConfigMap whenever the node role ARN or cluster name changes. 
    # Without triggers, a deleted/corrupted ConfigMap cannot be recovered by terraform apply — this resource is a no-op after first apply.
    node_role_arn = aws_iam_role.node_role.arn
    cluster_name  = aws_eks_cluster.cluster.name
  }

  provisioner "local-exec" {
    command = <<EOF
aws eks update-kubeconfig --region ${var.aws_region} --name ${var.cluster_name}

kubectl apply -f - <<YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: aws-auth
  namespace: kube-system
data:
  mapRoles: |
    - rolearn: ${aws_iam_role.node_role.arn}
      username: system:node:{{EC2PrivateDNSName}}
      groups:
        - system:bootstrappers
        - system:nodes
YAML
EOF
  }

  depends_on = [aws_eks_node_group.node_group]
}