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
      Resource = "arn:aws:secretsmanager:${var.region}:*:secret:${var.cluster_name}/*"
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

  depends_on = [aws_iam_role_policy_attachment.eso]
}

# ClusterSecretStore — cluster-scoped resource that tells ESO how to connect to
# AWS Secrets Manager and which service account to use for authentication.
# ExternalSecret resources in any namespace can reference this store by name.
resource "kubernetes_manifest" "cluster_secret_store" {
  manifest = {
    apiVersion = "external-secrets.io/v1beta1"
    kind       = "ClusterSecretStore"
    metadata = {
      name = "aws-secrets-manager"
    }
    spec = {
      provider = {
        aws = {
          service = "SecretsManager"
          region  = var.region
          auth = {
            jwt = {
              serviceAccountRef = {
                name      = "external-secrets"
                namespace = "external-secrets"
              }
            }
          }
        }
      }
    }
  }

  # The ClusterSecretStore CRD must exist (installed by the helm_release above)
  # before this manifest can be created.
  depends_on = [helm_release.eso]
}
