# Auto-detect the public IP of whoever runs terraform apply.
# Same pattern used in bootstrap-infra/security-groups.tf.
# This locks EKS public API access to your machine only — never 0.0.0.0/0

data "aws_caller_identity" "current" {}

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
    public_access_cidrs    = ["${var.jenkins_public_ip}/32", var.my_ip_cidr]
    # auto-locked to your IP at apply time | use this if endpoint_public_access = true
  }

  access_config {
    authentication_mode = "API_AND_CONFIG_MAP"
    # Set to false — we manage ALL access entries explicitly below.
    # With true, AWS silently creates a Jenkins entry outside Terraform's state,
    # which causes ResourceInUseException when Terraform also tries to create it.
    bootstrap_cluster_creator_admin_permissions = false
    # DEV NOTE: for security, this is set to false.  If you want to allow cluster creation via the AWS Console, set to true.
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

# to check for version update:
# aws eks describe-addon-versions 
#   --kubernetes-version 1.35 
#   --addon-name aws-ebs-csi-driver 
#   --query 'addons[0].addonVersions[0].addonVersion'
resource "aws_eks_addon" "ebs_csi" {
  cluster_name                = aws_eks_cluster.cluster.name
  addon_name                  = "aws-ebs-csi-driver"
  addon_version               = "v1.60.0-eksbuild.1"
  service_account_role_arn    = aws_iam_role.ebs_csi.arn
  resolve_conflicts_on_update = "OVERWRITE"

  depends_on = [
    aws_eks_node_group.node_group,
    aws_iam_role_policy_attachment.ebs_csi
  ]
}

# 

# NOTE: kubernetes_storage_class_v1.gp3 resource for gp3 StorageClass — used by Prometheus and Grafana PVC requests. This was intentionally moved to env/dev/main.tf.
# Reason: this resource uses the kubernetes provider, which is configured with
# local.cluster_endpoint = module.eks.cluster_endpoint. 
# During (terraform apply -target=module.eks), the endpoint is not yet in provider config — 
# it was "(known after apply)" at plan time, so Terraform initialises the kubernetes provider pointing at localhost:80. 
# The StorageClass apply then fails immediately with "dial tcp 127.0.0.1:80: connection refused".
#
# Moving it to the root module means it only runs in (full apply), 
# by which time module.eks is already in state, the endpoint is known, 
# and the kubernetes provider connects to the real cluster.

# Access Entry + Policy for Jenkins IAM user/role. This is the main way to access the cluster — the root user access entry is just a fallback to prevent lockout from the console.
resource "aws_eks_access_entry" "jenkins_admin" {
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = var.jenkins_role_arn
  type          = "STANDARD"

  depends_on = [aws_eks_cluster.cluster]
}

resource "aws_eks_access_policy_association" "jenkins_admin_policy" {
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = var.jenkins_role_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.jenkins_admin]
}

# Access Entry + Policy for Console IAM user/role (if different from Jenkins)
resource "aws_eks_access_entry" "console_admin" {
  count         = var.console_iam_arn != var.jenkins_role_arn ? 1 : 0
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = var.console_iam_arn
  type          = "STANDARD"

  depends_on = [aws_eks_cluster.cluster]
}

resource "aws_eks_access_policy_association" "console_admin_policy" {
  count         = var.console_iam_arn != var.jenkins_role_arn ? 1 : 0
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = var.console_iam_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.console_admin]
}

# Root account always gets console admin access.
# This is a permanent fix so the AWS Console never shows the
# "IAM principal doesn't have access" banner regardless of who runs apply.
resource "aws_eks_access_entry" "root_admin" {
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
  type          = "STANDARD"

  depends_on = [aws_eks_cluster.cluster]
}

resource "aws_eks_access_policy_association" "root_admin_policy" {
  cluster_name  = aws_eks_cluster.cluster.name
  principal_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.root_admin]
}