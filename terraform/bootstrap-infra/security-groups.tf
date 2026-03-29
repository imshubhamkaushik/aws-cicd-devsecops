# Jenkins security group
resource "aws_security_group" "jenkins" {
  name        = "jenkins-sg"
  description = "Security group for Jenkins"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "SSH for Ansible provisioning"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr] # need to set this to My IP only
  }

  ingress {
    description = "Jenkins UI and Jenkins webhook"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr] # need to set this to My IP only
  }

  egress {
    description = "Allow all outbound - Jenkins pulls plugins, pushes to ECR, calls EKS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# SonarQube security group
resource "aws_security_group" "sonar" {
  name        = "sonarqube-sg"
  description = "Security group for SonarQube"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "SSH for Ansible provisioning"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr] # need to set this to My IP only
  }

  ingress {
    description = "SonarQube UI and Jenkins webhook"
    from_port   = 9000
    to_port     = 9000
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr] # need to set this to My IP only
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}