resource "aws_iam_role" "jenkins_ec2_role" {
  name = "${var.project_name}-jenkins-ec2-role"

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
  name        = "${var.project_name}-jenkins-ec2-vpc-policy"
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
          "ec2:ModifySecurityGroupRules",
          "ec2:DescribeNetworkInterfaces"
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
  name        = "${var.project_name}-jenkins-rds-policy"
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
  name        = "${var.project_name}-jenkins-asg-policy"
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
  name        = "${var.project_name}-jenkins-eks-ecr-policy"
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
          "ecr:DeleteLifecyclePolicy",
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
  name        = "${var.project_name}-jenkins-kms-policy"
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

# -----------------------------------------------------------------------
# Permissions boundary — closes a privilege-escalation path.
#
# Jenkins's IAM policy (below) grants iam:CreateRole, iam:CreatePolicy,
# iam:PutRolePolicy/AttachRolePolicy, iam:PassRole, iam:CreateInstanceProfile
# and (separately) ec2:RunInstances. Resource-level IAM scoping restricts
# the NAME of a role/policy Jenkins can create — it does NOT restrict what
# permissions the policy DOCUMENT it creates can grant. Combined, those
# permissions let Jenkins: create a role named "catalogix-anything", attach
# an arbitrarily permissive inline policy to it, put it in an instance
# profile, launch an EC2 instance with that profile, and end up with
# effectively account-admin access. Anyone who can trigger a Jenkins
# Terraform run — or who compromises the Jenkins EC2 instance — inherits
# that path.
#
# This boundary is attached (via the permissions_boundary argument, plumbed
# through as var.permissions_boundary_arn) to every IAM role created by
# platform-infra's modules, and is enforced — not just suggested — via the
# Condition on RoleCreate below. A role with this boundary can never call
# IAM, STS AssumeRole*, Organizations, or Account-settings actions, no
# matter what gets attached to it. That closes the identity-escalation and
# cross-account-pivot path specifically.
#
# What this does NOT do: cap the role to only the services it legitimately
# needs (EC2/EKS/ECR/RDS/etc.) — it's a blunt "no IAM/STS/Org footguns"
# boundary, not a least-privilege one. A new role with an over-broad
# attached policy could still, e.g., touch unrelated S3 buckets or EC2
# resources. If you want that tightened further, replace the NotAction list
# below with an explicit allow-list of exactly the actions this account's
# infra needs.
# -----------------------------------------------------------------------
resource "aws_iam_policy" "jenkins_boundary" {
  name        = "${var.project_name}-jenkins-boundary"
  description = "Permissions boundary required on every IAM role Jenkins creates — caps maximum effective permissions regardless of what's attached to the role."

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "MaxPermissionsForJenkinsCreatedRoles"
        Effect = "Allow"
        NotAction = [
          "iam:*",
          "sts:AssumeRole",
          "sts:AssumeRoleWithSAML",
          "sts:AssumeRoleWithWebIdentity",
          "sts:GetFederationToken",
          "organizations:*",
          "account:*"
        ]
        Resource = "*"
      }
    ]
  })
}

# Custom policy for Jenkins to manage IAM
resource "aws_iam_policy" "jenkins_iam" {
  name        = "${var.project_name}-jenkins-iam-policy"
  description = "IAM role/policy/OIDC management for Terraform-managed resources"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # iam:CreateRole and iam:PassRole are separate actions. PassRole is required for Terraform to create IAM roles with the correct trust policy, but it does not grant permission to create the role itself. The CreateRole action is required to actually create the role.
        # Split out from role management below specifically so the boundary
        # condition can be applied only to role creation, not every role action. Without this split, 
        # Terraform fails to create new roles with an AccessDenied error because the boundary condition is applied to all role actions, not just creation.
        Sid    = "RoleCreate"
        Effect = "Allow"
        Action = ["iam:CreateRole"]
        Resource = [
          "arn:aws:iam::*:role/${var.project_name}-*",
          "arn:aws:iam::*:role/aws-service-role/*"
        ]
        Condition = {
          StringEquals = {
            "iam:PermissionsBoundary" = aws_iam_policy.jenkins_boundary.arn
          }
        }
      },
      {
        Sid    = "RoleManagement"
        Effect = "Allow"
        Action = [
          "iam:DeleteRole",
          "iam:GetRole",
          "iam:UpdateRole",
          "iam:UpdateAssumeRolePolicy",
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
          "arn:aws:iam::*:role/${var.project_name}-*",
          "arn:aws:iam::*:role/aws-service-role/*"
        ]
      },
      {
        # RoleManagement's Resource scope ("${var.project_name}-*") also
        # matches Jenkins's own role name and Sonar's role name — meaning
        # the permissions above, intended for roles Jenkins CREATES for EKS/
        # ALB/ESO/etc., would otherwise also let Jenkins attach more
        # policies to ITSELF directly, no new role required. Deny wins over
        # the Allow in RoleManagement, so this closes that path without
        # touching Jenkins's ability to manage every other role it owns.
        Sid    = "PreventJenkinsSelfModification"
        Effect = "Deny"
        Action = [
          "iam:AttachRolePolicy",
          "iam:PutRolePolicy",
          "iam:UpdateAssumeRolePolicy",
          "iam:PassRole"
        ]
        Resource = [
          aws_iam_role.jenkins_ec2_role.arn,
          aws_iam_role.sonar_ec2_role.arn
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
          "arn:aws:iam::*:policy/${var.project_name}-*"
        ]
      },
      {
        # Explicit Deny always wins, including over PolicyManagement's
        # wildcard match on this exact ARN (the boundary policy's name
        # matches ${var.project_name}-* like every other Jenkins-managed
        # policy). Without this, Jenkins could edit or delete its own
        # permissions boundary, which would silently defeat RoleCreate's
        # Condition above.
        Sid    = "ProtectPermissionsBoundary"
        Effect = "Deny"
        Action = [
          "iam:DeletePolicy",
          "iam:CreatePolicyVersion",
          "iam:DeletePolicyVersion",
          "iam:SetDefaultPolicyVersion"
        ]
        Resource = aws_iam_policy.jenkins_boundary.arn
      },
      {
        # Jenkins is not granted iam:PutRolePermissionsBoundary or
        # iam:DeleteRolePermissionsBoundary anywhere in this policy, so this
        # explicit Deny is currently redundant with that omission — it's
        # here so the guarantee survives a future edit that accidentally
        # adds those actions, rather than relying on nobody ever doing that.
        Sid    = "ProtectBoundaryAssignment"
        Effect = "Deny"
        Action = [
          "iam:DeleteRolePermissionsBoundary",
          "iam:PutRolePermissionsBoundary"
        ]
        Resource = "arn:aws:iam::*:role/${var.project_name}-*"
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
          "iam:TagInstanceProfile"
        ]
        Resource = "arn:aws:iam::*:instance-profile/${var.project_name}-*"
      },
      {
        # iam:ListInstanceProfilesForRole operates on a role ARN, not an
        # instance-profile ARN, so it must be in its own statement with
        # role/* resources. Placing it under instance-profile/* causes a
        # 403 AccessDenied when Terraform destroys EKS node/addon roles.
        Sid    = "ListInstanceProfilesForRole"
        Effect = "Allow"
        Action = ["iam:ListInstanceProfilesForRole"]
        Resource = [
          "arn:aws:iam::*:role/${var.project_name}-*"
        ]
      },
      {
        # EKS calls iam:GetRole on AWSServiceRoleForAmazonEKSNodegroup to check
        # if the SLR already exists before creating it. Scoping to
        # aws-service-role/* is insufficient — AWS requires Resource "*" for
        # this SLR existence check to pass.
        Sid      = "SLRDescribe"
        Effect   = "Allow"
        Action   = ["iam:GetRole"]
        Resource = "*"
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
        Resource = "arn:aws:iam::*:role/aws-service-role/*"
        Condition = {
          StringEquals = {
            "iam:AWSServiceName" = "eks-nodegroup.amazonaws.com"
          }
        }
      },
      {
        Sid      = "ServiceLinkedRoleForRDS"
        Effect   = "Allow"
        Action   = ["iam:CreateServiceLinkedRole", "iam:GetRole"]
        Resource = "*"
        Condition = {
          StringEquals = {
            "iam:AWSServiceName" = "rds.amazonaws.com"
          }
        }
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
  name        = "${var.project_name}-jenkins-s3-ops-policy"
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
      # ssm:DescribeParameters must be Resource = "*" — it is a service-level
      # listing action that AWS evaluates against the account root, not a parameter path.
      {
        Sid      = "SSMDescribeParameters"
        Effect   = "Allow"
        Action   = ["ssm:DescribeParameters"]
        Resource = "*"
      },
      {
        Sid    = "SSMParameterReadWrite"
        Effect = "Allow"
        Action = [
          "ssm:GetParameter", "ssm:GetParameters",
          "ssm:PutParameter", "ssm:DeleteParameter",
          "ssm:AddTagsToResource", "ssm:ListTagsForResource"
        ]
        # Scoped to /${var.project_name} prefix — covers /catalogix-cluster-dev/rds-endpoint etc.
        Resource = [
          "arn:aws:ssm:${var.aws_region}:*:parameter/${var.project_name}-*"
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
          "secretsmanager:RestoreSecret",

          # Required by Terraform AWS provider during refresh/read
          "secretsmanager:GetResourcePolicy",
          "secretsmanager:PutResourcePolicy",
          "secretsmanager:DeleteResourcePolicy",

          # Often needed by Terraform/provider internals
          "secretsmanager:ListSecrets"
        ]
        Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:${var.project_name}-*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "jenkins_s3_ops" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = aws_iam_policy.jenkins_s3_ops.arn
}

# Jenkins IAM instance profile
resource "aws_iam_instance_profile" "jenkins_profile" {
  name = "${var.project_name}-jenkins-instance-profile"
  role = aws_iam_role.jenkins_ec2_role.name
}

# SonarQube IAM role -- No policy attachments - SonarQube needs no AWS permission
resource "aws_iam_role" "sonar_ec2_role" {
  name = "${var.project_name}-sonar-ec2-role"

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
  name = "${var.project_name}-sonar-instance-profile"
  role = aws_iam_role.sonar_ec2_role.name
}
