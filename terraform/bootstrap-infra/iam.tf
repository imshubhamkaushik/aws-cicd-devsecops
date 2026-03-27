resource "aws_iam_role" "jenkins_ec2_role" {
  name = "${var.name}-jenkins-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# DEV NOTE: AdministratorAccess is intentionally used here for simplicity in a dev/learning environment. 
# Jenkins needs to push to ECR, manage EKS, apply Terraform (which creates IAM roles, EKS, RDS, etc.), and read Secrets Manager.
# Scoping all of that precisely is complex for a dev project.

resource "aws_iam_role_policy_attachment" "jenkins_admin" {
  role       = aws_iam_role.jenkins_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

# # SonarQube IAM role
# # No policy attachments - SonarQube needs no AWS permission
# resource "aws_iam_role" "sonar_ec2_role" {
#   name = "${var.name}-sonar-ec2-role"

#   assume_role_policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [{
#       Action    = "sts:AssumeRole"
#       Effect    = "Allow"
#       Principal = { Service = "ec2.amazonaws.com" }
#     }]
#   })
# }