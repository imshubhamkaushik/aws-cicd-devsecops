resource "aws_iam_role" "ec2_role" {
  name = "${var.name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# DEV NOTE: AdministratorAccess is intentionally used here for simplicity
# in a dev/learning environment — Jenkins needs to push to ECR, manage EKS,
# apply Terraform (which creates IAM roles, EKS, RDS, etc.), and read Secrets Manager.
# Scoping all of that precisely is complex for a fresher project.
#
# For production, replace this with a custom policy that grants only:
#   ecr:GetAuthorizationToken, ecr:BatchCheckLayerAvailability, ecr:PutImage (for ECR)
#   eks:DescribeCluster (for kubeconfig)
#   secretsmanager:GetSecretValue (for reading DB creds)
#   s3:GetObject, s3:PutObject (for Terraform state)
#   iam:PassRole (scoped to specific roles)

resource "aws_iam_role_policy_attachment" "jenkins_admin" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}