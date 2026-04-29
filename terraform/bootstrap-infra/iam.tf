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

# Custom policy for Jenkins to manage VPC and network resources — scoped to what Terraform actually creates
resource "aws_iam_policy" "jenkins_vpc" {
  name        = "${var.ec2_name}-jenkins-vpc-policy"
  description = "VPC, subnet, IGW, NAT, EIP, route table management for Terraform"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "VPCManagement"
        Effect = "Allow"
        Action = [
          "ec2:CreateVpc", "ec2:DeleteVpc", "ec2:DescribeVpcs",
          "ec2:ModifyVpcAttribute", "ec2:DescribeVpcAttribute",
          "ec2:CreateSubnet", "ec2:DeleteSubnet", "ec2:DescribeSubnets",
          "ec2:ModifySubnetAttribute",
          "ec2:CreateInternetGateway", "ec2:DeleteInternetGateway",
          "ec2:AttachInternetGateway", "ec2:DetachInternetGateway",
          "ec2:DescribeInternetGateways",
          "ec2:CreateNatGateway", "ec2:DeleteNatGateway", "ec2:DescribeNatGateways",
          "ec2:AllocateAddress", "ec2:ReleaseAddress",
          "ec2:AssociateAddress", "ec2:DisassociateAddress", "ec2:DescribeAddresses",
          "ec2:CreateRouteTable", "ec2:DeleteRouteTable", "ec2:DescribeRouteTables",
          "ec2:CreateRoute", "ec2:DeleteRoute",
          "ec2:AssociateRouteTable", "ec2:DisassociateRouteTable",
          "ec2:CreateTags", "ec2:DeleteTags", "ec2:DescribeTags",
          "ec2:DescribeAvailabilityZones",
          "ec2:DescribeAccountAttributes"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_vpc" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_vpc.arn
}

# Custom policy for Jenkins to manage EC2 compute resources — instances, security groups, launch templates, volumes
resource "aws_iam_policy" "jenkins_compute" {
  name        = "${var.ec2_name}-jenkins-compute-policy"
  description = "EC2 instances, security groups, launch templates, EBS volumes for Terraform"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SecurityGroupManagement"
        Effect = "Allow"
        Action = [
          "ec2:CreateSecurityGroup", "ec2:DeleteSecurityGroup",
          "ec2:DescribeSecurityGroups", "ec2:DescribeSecurityGroupRules",
          "ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress",
          "ec2:AuthorizeSecurityGroupEgress", "ec2:RevokeSecurityGroupEgress",
          "ec2:UpdateSecurityGroupRuleDescriptionsIngress",
          "ec2:UpdateSecurityGroupRuleDescriptionsEgress",
          "ec2:ModifySecurityGroupRules"
        ]
        Resource = "*"
      },
      {
        Sid    = "InstanceManagement"
        Effect = "Allow"
        Action = [
          "ec2:RunInstances", "ec2:TerminateInstances",
          "ec2:StartInstances", "ec2:StopInstances",
          "ec2:DescribeInstances", "ec2:DescribeInstanceStatus",
          "ec2:DescribeInstanceTypes", "ec2:DescribeInstanceAttribute",
          "ec2:ModifyInstanceAttribute",
          "ec2:DescribeImages", "ec2:DescribeKeyPairs"
        ]
        Resource = "*"
      },
      {
        Sid    = "LaunchTemplateManagement"
        Effect = "Allow"
        Action = [
          "ec2:CreateLaunchTemplate", "ec2:DeleteLaunchTemplate",
          "ec2:DescribeLaunchTemplates", "ec2:DescribeLaunchTemplateVersions",
          "ec2:ModifyLaunchTemplate",
          "ec2:CreateLaunchTemplateVersion", "ec2:DeleteLaunchTemplateVersions",
          "ec2:GetLaunchTemplateData"
        ]
        Resource = "*"
      },
      {
        Sid    = "VolumeManagement"
        Effect = "Allow"
        Action = [
          "ec2:CreateVolume", "ec2:DeleteVolume",
          "ec2:AttachVolume", "ec2:DetachVolume",
          "ec2:DescribeVolumes", "ec2:ModifyVolume",
          "ec2:DescribeVolumesModifications"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_compute" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_compute.arn
}

# Custom policy for Jenkins to manage RDS instances and subnet groups
resource "aws_iam_policy" "jenkins_rds" {
  name        = "${var.ec2_name}-jenkins-rds-policy"
  description = "RDS instance and subnet group management for Terraform"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "RDSManagement"
        Effect = "Allow"
        Action = [
          "rds:CreateDBInstance", "rds:DeleteDBInstance",
          "rds:DescribeDBInstances", "rds:ModifyDBInstance",
          "rds:RebootDBInstance",
          "rds:CreateDBSubnetGroup", "rds:DeleteDBSubnetGroup",
          "rds:DescribeDBSubnetGroups", "rds:ModifyDBSubnetGroup",
          "rds:DescribeDBEngineVersions",
          "rds:DescribeOrderableDBInstanceOptions",
          "rds:DescribeDBParameterGroups",
          "rds:AddTagsToResource", "rds:RemoveTagsFromResource",
          "rds:ListTagsForResource"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_rds" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_rds.arn
}

# Custom policy for Jenkins to manage Auto Scaling Groups for EKS node groups
resource "aws_iam_policy" "jenkins_asg" {
  name        = "${var.ec2_name}-jenkins-asg-policy"
  description = "Auto Scaling group management for EKS node groups via Terraform"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AutoScalingManagement"
        Effect = "Allow"
        Action = [
          "autoscaling:CreateAutoScalingGroup",
          "autoscaling:UpdateAutoScalingGroup",
          "autoscaling:DeleteAutoScalingGroup",
          "autoscaling:DescribeAutoScalingGroups",
          "autoscaling:DescribeAutoScalingInstances",
          "autoscaling:DescribeScalingActivities",
          "autoscaling:DescribeTerminationPolicyTypes",
          "autoscaling:SetDesiredCapacity",
          "autoscaling:TerminateInstanceInAutoScalingGroup",
          "autoscaling:CreateOrUpdateTags",
          "autoscaling:DeleteTags",
          "autoscaling:DescribeTags",
          "autoscaling:PutLifecycleHook",
          "autoscaling:DeleteLifecycleHook",
          "autoscaling:DescribeLifecycleHooks"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_asg" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_asg.arn
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
        Resource = [
          "arn:aws:iam::*:role/${var.ec2_name}-*",
          "arn:aws:iam::*:role/${var.cluster_name}-*"
        ]
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

# Custom policy for Jenkins to manage CloudWatch Logs, SSM Parameter Store, and STS for Jenkins operations and monitoring
resource "aws_iam_policy" "jenkins_ops" {
  name        = "${var.ec2_name}-jenkins-ops-policy"
  description = "SSM Parameter Store, STS caller identity, and CloudWatch Logs for Jenkins CI/CD"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SSMParameterReadWrite"
        Effect = "Allow"
        Action = [
          "ssm:GetParameter", "ssm:GetParameters",
          "ssm:PutParameter", "ssm:DeleteParameter",
          "ssm:DescribeParameters",
          "ssm:AddTagsToResource", "ssm:ListTagsForResource"
        ]
        # Scoped to /${var.ec2_name}/ prefix — covers /catalogix/dev/rds-endpoint etc.
        Resource = [
          "arn:aws:ssm:${var.aws_region}:*:parameter/${var.ec2_name}/*",
          "arn:aws:ssm:${var.aws_region}:*:parameter/${var.cluster_name}-*"
        ]
      },
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

resource "aws_iam_role_policy_attachment" "jenkins_ops" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_ops.arn
}

# Custom policy for Jenkins to manage EKS
resource "aws_iam_policy" "jenkins_eks_access" {
  name = "${var.ec2_name}-jenkins-eks-access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "eks:DescribeCluster"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_eks_access" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_eks_access.arn
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

