terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 3.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 3.0"
    }
    # Replaces local-exec for CRD-based resources.
    # Unlike hashicorp/kubernetes, this does NOT validate CRD schemas at plan time.
    # Safe to use on fresh deploys where the cluster doesn't exist yet at plan time.
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.14"
    }
  }
}

# AWS data sources for dynamic values and to avoid hardcoding ARNs or account IDs.
data "aws_caller_identity" "current" {}

# IAM Policy — scoped to only reading secrets from Secrets Manager.
# ESO only needs GetSecretValue and DescribeSecret — nothing else.
resource "aws_iam_policy" "eso" {
  name        = "${var.cluster_name}-eso-secrets-policy"
  description = "Allows External Secrets Operator to read from AWS Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ]
      # Scoped to secrets whose name starts with the cluster name.
      # The trailing /* covers both the secret itself and any version stages.
      # Example: catalogix-dev/db-credentials matches catalogix-dev/*
      Resource = "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.cluster_name}/*"
      # PROD NOTE: scope Resource to specific secret ARNs for least-privilege
    }]
  })
}

# IRSA trust policy — allows only the ESO service account to assume this role.
# No other pod in the cluster can use it.
data "aws_iam_policy_document" "eso_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider}:sub"
      values   = ["system:serviceaccount:external-secrets:external-secrets"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eso" {
  name               = "${var.cluster_name}-eso-role"
  assume_role_policy = data.aws_iam_policy_document.eso_assume_role.json
}

resource "aws_iam_role_policy_attachment" "eso" {
  role       = aws_iam_role.eso.name
  policy_arn = aws_iam_policy.eso.arn
}

# Install External Secrets Operator into the cluster.
# wait = true ensures all CRDs (including ClusterSecretStore) are registered
# before the kubernetes_manifest below tries to create an instance of one.
resource "helm_release" "eso" {

  name             = "external-secrets"
  namespace        = "external-secrets"
  create_namespace = true
  repository       = "https://charts.external-secrets.io"
  chart            = "external-secrets"
  version          = "0.9.20"
  wait             = true
  timeout          = 300

  set = [
    {
      name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
      value = aws_iam_role.eso.arn
    }
  ]

  depends_on = [
    aws_iam_role_policy_attachment.eso
  ]
}

# ClusterSecretStore — applied via kubectl instead of kubernetes_manifest.
resource "kubectl_manifest" "cluster_secret_store" {

  yaml_body = <<-YAML
    apiVersion: external-secrets.io/v1beta1
    kind: ClusterSecretStore
    metadata:
      name: aws-secrets-manager
    spec:
      provider:
        aws:
          service: SecretsManager
          region: ${var.region}
          auth:
            jwt:
              serviceAccountRef:
                name: external-secrets
                namespace: external-secrets
  YAML

  # helm_release.eso installs the ClusterSecretStore CRD.
  # kubectl_manifest can only apply an instance of it after the CRD exists.
  depends_on = [helm_release.eso]
}