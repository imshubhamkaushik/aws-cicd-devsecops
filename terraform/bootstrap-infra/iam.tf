resource "aws_iam_role" "jenkins_ec2_role" {
  name = "${var.ec2_name}-jenkins-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# AWS Managed Policies for Jenkins
resource "aws_iam_role_policy_attachment" "jenkins_ec2_full" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2FullAccess"
}

resource "aws_iam_role_policy_attachment" "jenkins_rds_full" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonRDSFullAccess"
}

resource "aws_iam_role_policy_attachment" "jenkins_autoscaling" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AutoScalingFullAccess"
}

resource "aws_iam_role_policy_attachment" "jenkins_elb" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/ElasticLoadBalancingFullAccess"
}

# Custom policy for Jenkins to manage ECR
resource "aws_iam_policy" "jenkins_ecr" {
  name        = "${var.cluster_name}-jenkins-ecr-policy"
  description = "ECR push/pull and repository lifecycle management for Jenkins"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ECRLogin"
        Effect = "Allow"
        Action = ["ecr:GetAuthorizationToken"]
        # Cannot be scoped to a specific repo — this is an AWS API constraint.
        Resource = "*"
      },
      {
        Sid    = "ECRRepoOperations"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:DescribeRepositories",
          "ecr:CreateRepository",
          "ecr:DeleteRepository",
          "ecr:ListImages",
          "ecr:DescribeImages",
          "ecr:PutLifecyclePolicy",
          "ecr:GetLifecyclePolicy",
          "ecr:TagResource",
          "ecr:ListTagsForResource"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_ecr" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_ecr.arn
}

# Custom policy for Jenkins to manage EKS
resource "aws_iam_policy" "jenkins_eks" {
  name        = "${var.cluster_name}-jenkins-eks-policy"
  description = "Full EKS cluster and node group management for Jenkins / Terraform"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "EKSFullManagement"
      Effect = "Allow"
      Action = [
        "eks:CreateCluster",
        "eks:DeleteCluster",
        "eks:DescribeCluster",
        "eks:ListClusters",
        "eks:UpdateClusterConfig",
        "eks:UpdateClusterVersion",
        "eks:TagResource",
        "eks:UntagResource",
        "eks:ListTagsForResource",
        "eks:AccessKubernetesApi",
        "eks:CreateNodegroup",
        "eks:DeleteNodegroup",
        "eks:DescribeNodegroup",
        "eks:ListNodegroups",
        "eks:UpdateNodegroupConfig",
        "eks:UpdateNodegroupVersion",
        "eks:CreateAddon",
        "eks:DeleteAddon",
        "eks:DescribeAddon",
        "eks:ListAddons",
        "eks:UpdateAddon",
        "eks:DescribeAddonVersions",
        "eks:DescribeAddonConfiguration",
        "eks:AssociateIdentityProviderConfig",
        "eks:DisassociateIdentityProviderConfig",
        "eks:DescribeIdentityProviderConfig",
        "eks:ListIdentityProviderConfigs"
      ]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_eks" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_eks.arn
}

# Custom policy for Jenkins to manage IAM
resource "aws_iam_policy" "jenkins_iam" {
  name        = "${var.ec2_name}-jenkins-iam-policy"
  description = "IAM role/policy/OIDC management for Terraform-managed resources"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "RoleManagement"
        Effect = "Allow"
        Action = [
          "iam:CreateRole",
          "iam:DeleteRole",
          "iam:GetRole",
          "iam:UpdateRole",
          "iam:TagRole",
          "iam:UntagRole",
          "iam:ListRoleTags",
          "iam:AttachRolePolicy",
          "iam:DetachRolePolicy",
          "iam:ListAttachedRolePolicies",
          "iam:ListRolePolicies",
          "iam:PutRolePolicy",
          "iam:DeleteRolePolicy",
          "iam:GetRolePolicy",
          "iam:PassRole"
        ]
        Resource = "arn:aws:iam::*:role/${var.ec2_name}-*"
      },
      {
        Sid    = "PolicyManagement"
        Effect = "Allow"
        Action = [
          "iam:CreatePolicy",
          "iam:DeletePolicy",
          "iam:GetPolicy",
          "iam:GetPolicyVersion",
          "iam:CreatePolicyVersion",
          "iam:DeletePolicyVersion",
          "iam:ListPolicyVersions",
          "iam:ListPolicies",
          "iam:TagPolicy",
          "iam:UntagPolicy"
        ]
        Resource = "arn:aws:iam::*:policy/${var.ec2_name}-*"
      },
      {
        Sid    = "InstanceProfileManagement"
        Effect = "Allow"
        Action = [
          "iam:CreateInstanceProfile",
          "iam:DeleteInstanceProfile",
          "iam:GetInstanceProfile",
          "iam:AddRoleToInstanceProfile",
          "iam:RemoveRoleFromInstanceProfile",
          "iam:ListInstanceProfiles",
          "iam:ListInstanceProfilesForRole",
          "iam:TagInstanceProfile"
        ]
        Resource = "arn:aws:iam::*:instance-profile/${var.ec2_name}-*"
      },
      {
        Sid    = "OIDCProviderManagement"
        Effect = "Allow"
        Action = [
          "iam:CreateOpenIDConnectProvider",
          "iam:DeleteOpenIDConnectProvider",
          "iam:GetOpenIDConnectProvider",
          "iam:UpdateOpenIDConnectProviderThumbprint",
          "iam:TagOpenIDConnectProvider",
          "iam:ListOpenIDConnectProviders"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_iam" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_iam.arn
}

# Custom policy for Jenkins to manage S3 (for Terraform state)
resource "aws_iam_policy" "jenkins_s3" {
  name        = "${var.ec2_name}-jenkins-s3-policy"
  description = "S3 read/write access scoped to the Terraform state bucket"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "TFStateBucketList"
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucketVersioning",
          "s3:GetBucketLocation",
          "s3:GetBucketAcl"
        ]
        Resource = "arn:aws:s3:::catalogix-tfstate"
      },
      {
        Sid    = "TFStateObjects"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "arn:aws:s3:::catalogix-tfstate/*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_s3" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_s3.arn
}

# Custom policy for Jenkins to manage Secrets Manager (for storing DB credentials, etc.)
resource "aws_iam_policy" "jenkins_secrets" {
  name        = "${var.ec2_name}-jenkins-secrets-policy"
  description = "Secrets Manager access scoped to catalogix-* secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "SecretsManagerCatalogix"
      Effect = "Allow"
      Action = [
        "secretsmanager:CreateSecret",
        "secretsmanager:DeleteSecret",
        "secretsmanager:GetSecretValue",
        "secretsmanager:PutSecretValue",
        "secretsmanager:DescribeSecret",
        "secretsmanager:UpdateSecret",
        "secretsmanager:TagResource",
        "secretsmanager:ListSecretVersionIds",
        "secretsmanager:RestoreSecret"
      ]
      Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:catalogix-*"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_secrets" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_secrets.arn
}

# Custom policy for Jenkins to manage CloudWatch Logs
resource "aws_iam_policy" "jenkins_misc" {
  name        = "${var.ec2_name}-jenkins-misc-policy"
  description = "STS caller identity and CloudWatch Logs for EKS control-plane logging"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "STSCallerIdentity"
        Effect   = "Allow"
        Action   = ["sts:GetCallerIdentity"]
        Resource = "*"
      },
      {
        Sid    = "CloudWatchLogsEKS"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:DeleteLogGroup",
          "logs:DescribeLogGroups",
          "logs:PutRetentionPolicy",
          "logs:DeleteRetentionPolicy",
          "logs:TagLogGroup",
          "logs:ListTagsLogGroup",
          "logs:ListTagsForResource",
          "logs:TagResource"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_misc" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_misc.arn
}

resource "aws_iam_instance_profile" "jenkins_profile" {
  name = "${var.ec2_name}-jenkins-instance-profile"
  role = aws_iam_role.jenkins_ec2_role.name
}

# SonarQube IAM role
# No policy attachments - SonarQube needs no AWS permission
resource "aws_iam_role" "sonar_ec2_role" {
  name = "${var.ec2_name}-sonar-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_instance_profile" "sonar_profile" {
  name = "${var.ec2_name}-sonar-instance-profile"
  role = aws_iam_role.sonar_ec2_role.name
}