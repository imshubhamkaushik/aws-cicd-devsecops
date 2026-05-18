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

# Custom policy for Jenkins to manage EC2 compute resources — instances, security groups, launch templates, volumes etc and VPC and network resources
resource "aws_iam_policy" "jenkins_ec2_vpc" {
  name        = "${var.ec2_name}-jenkins-ec2-vpc-policy"
  description = "Policy for VPC management (VPC, subnet, IGW, NAT, EIP, route table management) and EC2 management (EC2 instances, security groups, launch templates, EBS volumes) for Terraform"

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
      },
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

resource "aws_iam_role_policy_attachment" "jenkins_ec2_vpc" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_ec2_vpc.arn
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

# Custom policy for Jenkins to manage EKS and ECR
resource "aws_iam_policy" "jenkins_eks_ecr" {
  name        = "${var.ec2_name}-jenkins-eks-ecr-policy"
  description = "EKS cluster management and ECR push/pull for Jenkins"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
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
      },
      {
        # platform-infra creates aws_eks_access_entry and
        # aws_eks_access_policy_association resources. These API calls are
        # separate from the core EKS cluster actions above and must be
        # explicitly granted — they are NOT included in any AWS managed policy.
        Sid    = "EKSAccessEntryManagement"
        Effect = "Allow"
        Action = [
          "eks:CreateAccessEntry",
          "eks:DeleteAccessEntry",
          "eks:DescribeAccessEntry",
          "eks:UpdateAccessEntry",
          "eks:ListAccessEntries",
          "eks:AssociateAccessPolicy",
          "eks:DisassociateAccessPolicy",
          "eks:ListAssociatedAccessPolicies",
          "eks:ListAccessPolicies"
        ]
        Resource = "*"
      },
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

resource "aws_iam_role_policy_attachment" "jenkins_eks_ecr" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_eks_ecr.arn
}

# KMS policy — required because platform-infra creates aws_kms_key.eks for
# EKS secrets encryption. Without these actions, terraform apply in
# platform-infra fails with AccessDeniedException on the KMS API calls.
resource "aws_iam_policy" "jenkins_kms" {
  name        = "${var.ec2_name}-jenkins-kms-policy"
  description = "KMS key lifecycle management for EKS secrets encryption key"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "KMSKeyManagement"
        Effect = "Allow"
        Action = [
          "kms:CreateKey",
          "kms:DescribeKey",
          "kms:GetKeyPolicy",
          "kms:GetKeyRotationStatus",
          "kms:ListResourceTags",
          "kms:ScheduleKeyDeletion",
          "kms:CancelKeyDeletion",
          "kms:EnableKeyRotation",
          "kms:DisableKeyRotation",
          "kms:PutKeyPolicy",
          "kms:TagResource",
          "kms:UntagResource",
          "kms:ListKeys",
          "kms:ListAliases",
          "kms:CreateAlias",
          "kms:DeleteAlias",
          "kms:UpdateAlias",
          "kms:CreateGrant"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_kms" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_kms.arn
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
        Resource = [
          "arn:aws:iam::*:policy/${var.ec2_name}-*",
          "arn:aws:iam::*:policy/${var.cluster_name}-*"
        ]
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
      },
      {
        Sid    = "ServiceLinkedRoleForEKS"
        Effect = "Allow"
        Action = [
          "iam:CreateServiceLinkedRole"
        ]
        Resource = "arn:aws:iam::*:role/aws-service-role/eks.amazonaws.com/*"
        Condition = {
          StringEquals = {
            "iam:AWSServiceName" = "eks.amazonaws.com"
          }
        }
      },
      {
        Sid    = "ServiceLinkedRoleForEKSNodegroup"
        Effect = "Allow"
        Action = [
          "iam:CreateServiceLinkedRole"
        ]
        Resource = "arn:aws:iam::*:role/aws-service-role/eks-nodegroup.amazonaws.com/*"
        Condition = {
          StringEquals = {
            "iam:AWSServiceName" = "eks-nodegroup.amazonaws.com"
          }
        }
      },
      {
        Sid    = "SLRGetRole"
        Effect = "Allow"
        Action = ["iam:GetRole"]
        Resource = [
          "arn:aws:iam::*:role/aws-service-role/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_iam" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_iam.arn
}

# Custom policy for Jenkins to manage S3, CloudWatch Logs, SSM Parameter Store, Secrets Manager and STS for Jenkins operations and monitoring
resource "aws_iam_policy" "jenkins_s3_ops" {
  name        = "${var.ec2_name}-jenkins-s3-ops-policy"
  description = "S3, SSM Parameter Store, Secrets Manager,STS caller identity, and CloudWatch Logs for Jenkins CI/CD"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # S3
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
      },
      # SSM Parameter Store
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
      # STS
      {
        Sid      = "STSCallerIdentity"
        Effect   = "Allow"
        Action   = ["sts:GetCallerIdentity"]
        Resource = "*"
      },
      # CloudWatch Logs
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
      },
      # Secrets Manager
      {
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
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_s3_ops" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_s3_ops.arn
}

# Jenkns IAM instance profile
resource "aws_iam_instance_profile" "jenkins_profile" {
  name = "${var.ec2_name}-jenkins-instance-profile"
  role = aws_iam_role.jenkins_ec2_role.name
}

# SonarQube IAM role -- No policy attachments - SonarQube needs no AWS permission
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

# SonarQube IAM instance profile
resource "aws_iam_instance_profile" "sonar_profile" {
  name = "${var.ec2_name}-sonar-instance-profile"
  role = aws_iam_role.sonar_ec2_role.name
}
