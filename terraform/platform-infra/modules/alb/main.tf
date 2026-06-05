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
  }
}

# Policy JSON is committed to the repo — no external HTTP call at plan time.
# To update: download a new version of the file and commit it.
# Source: https://github.com/kubernetes-sigs/aws-load-balancer-controller/blob/v2.11.0/docs/install/iam_policy.json

resource "aws_iam_policy" "alb" {
  name        = "${var.cluster_name}-alb-policy"
  description = "Scoped IAM policy for the AWS Load Balancer Controller"
  policy      = file("${path.module}/iam_policy.json")
}

# ALB IAM Role for AWS Load Balancer Controller
data "aws_iam_policy_document" "alb_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider}:sub"
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "alb" {
  name               = "${var.cluster_name}-alb-role"
  assume_role_policy = data.aws_iam_policy_document.alb_assume_role.json
}

resource "aws_iam_role_policy_attachment" "alb" {
  role       = aws_iam_role.alb.name
  policy_arn = aws_iam_policy.alb.arn
}