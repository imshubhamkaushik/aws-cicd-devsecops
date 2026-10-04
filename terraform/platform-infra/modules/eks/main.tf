
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
  name                 = "${var.cluster_name}-cluster-role"
  assume_role_policy   = data.aws_iam_policy_document.cluster_assume_role.json
  permissions_boundary = var.permissions_boundary_arn
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
    # Public endpoint is limited to the Jenkins and operator IPs; use private-only in production.
    endpoint_public_access = true
    public_access_cidrs    = ["${var.jenkins_public_ip}/32", var.my_ip_cidr]
  }

  access_config {
    authentication_mode = "API_AND_CONFIG_MAP"
    # All access entries are managed explicitly below. Letting AWS create the
    # creator entry would conflict with them (ResourceInUseException).
    bootstrap_cluster_creator_admin_permissions = false
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
  name                 = "${var.cluster_name}-node-role"
  assume_role_policy   = data.aws_iam_policy_document.node_assume_role.json
  permissions_boundary = var.permissions_boundary_arn
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
  disk_size      = var.disk_size

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
  name                 = "${var.cluster_name}-ebs-csi-role"
  assume_role_policy   = data.aws_iam_policy_document.ebs_csi_assume_role.json
  permissions_boundary = var.permissions_boundary_arn
}

resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.ebs_csi.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

# Check for newer versions: aws eks describe-addon-versions --kubernetes-version 1.35 --addon-name aws-ebs-csi-driver
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

# The gp3 StorageClass (used by Prometheus and Grafana PVCs) lives in env/dev, not
# here: the kubernetes provider needs this cluster's endpoint, which is unknown
# during a targeted apply of module.eks.

# Access entry for the Jenkins role: the primary way to administer the cluster.
# The root entry below is only a console fallback.
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

# Root account always gets console admin access, so the AWS Console works
# regardless of who ran apply.
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