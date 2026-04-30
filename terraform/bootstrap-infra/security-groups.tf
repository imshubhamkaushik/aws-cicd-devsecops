# Jenkins security group
data "http" "my_ip" {
  url = "https://checkip.amazonaws.com" # same service AWS Console uses
}

locals {
  my_ip_cidr = "${chomp(data.http.my_ip.response_body)}/32"
  #                      ↑ strips trailing newline    ↑ /32 = single host
}

resource "aws_security_group" "jenkins" {
  name        = "jenkins-sg"
  description = "Security group for Jenkins"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "SSH for Ansible provisioning"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [local.my_ip_cidr] # need to set this to My IP only
  }

  ingress {
    description = "Jenkins UI and Jenkins webhook"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [local.my_ip_cidr] # need to set this to My IP only
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
    cidr_blocks = [local.my_ip_cidr] # need to set this to My IP only
  }

  ingress {
    description = "SonarQube UI - admin browser access via SSH tunnel"
    from_port   = 9000
    to_port     = 9000
    protocol    = "tcp"
    cidr_blocks = [local.my_ip_cidr] # need to set this to My IP only
  }

  

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# -----------------------------------------------------------------------
# Cross-referencing rules extracted to break the SG → SG cycle.
# Both SG blocks above are now cycle-free. Terraform creates them first,
# then attaches these rules once both SG IDs are known.
# -----------------------------------------------------------------------

# Sonar SG rules that reference Jenkins SG
resource "aws_security_group_rule" "sonar_allow_jenkins_ssh" {
  description              = "Ansible ProxyJump - SSH from Jenkins to reach SonarQube private subnet"
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  security_group_id        = aws_security_group.sonar.id
  source_security_group_id = aws_security_group.jenkins.id
}

resource "aws_security_group_rule" "sonar_allow_jenkins_9000" {
  description              = "Jenkins pipeline - SonarQube analysis and Quality gate webhook"
  type                     = "ingress"
  from_port                = 9000
  to_port                  = 9000
  protocol                 = "tcp"
  security_group_id        = aws_security_group.sonar.id
  source_security_group_id = aws_security_group.jenkins.id
}

# Jenkins SG rule that references Sonar SG (the new webhook callback rule)
resource "aws_security_group_rule" "jenkins_allow_sonar_webhook" {
  description              = "SonarQube webhook callback to Jenkins"
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = aws_security_group.jenkins.id
  source_security_group_id = aws_security_group.sonar.id
}