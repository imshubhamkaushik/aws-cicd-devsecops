# Jenkins security group
data "http" "my_ip" {
  url = "https://checkip.amazonaws.com" # same service AWS Console uses
}

locals {
  my_ip_cidr = "${chomp(data.http.my_ip.response_body)}/32"
  #                      ↑ strips trailing newline    ↑ /32 = single host
}

# All rules are standalone aws_security_group_rule resources; the SGs have no
# inline ingress/egress blocks. Mixing both styles causes perpetual plan drift.

# Jenkins Security Group
resource "aws_security_group" "jenkins" {
  name        = "jenkins-sg"
  description = "Security group for Jenkins"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.cluster_name}-jenkins-sg"
  }
}

# SonarQube security group
resource "aws_security_group" "sonar" {
  name        = "sonarqube-sg"
  description = "Security group for SonarQube"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.cluster_name}-sonarqube-sg"
  }
}

# Jenkins rules

resource "aws_security_group_rule" "jenkins_ingress_ssh" {
  description       = "SSH for Ansible provisioning - locked to your IP at apply time"
  type              = "ingress"
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  cidr_blocks       = [local.my_ip_cidr]
  security_group_id = aws_security_group.jenkins.id
}

resource "aws_security_group_rule" "jenkins_ingress_ui" {
  description       = "Jenkins UI and webhook - locked to your IP at apply time"
  type              = "ingress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  cidr_blocks       = [local.my_ip_cidr]
  security_group_id = aws_security_group.jenkins.id
}

resource "aws_security_group_rule" "jenkins_ingress_sonar_webhook" {
  description              = "SonarQube Quality Gate webhook callback to Jenkins"
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.sonar.id
  security_group_id        = aws_security_group.jenkins.id
}

resource "aws_security_group_rule" "jenkins_egress_all" {
  description       = "Allow all outbound - Jenkins pulls plugins, pushes to ECR, calls EKS"
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.jenkins.id
}

# SonarQube rules
# SonarQube sits in a private subnet with no inbound internet path, so there
# are no rules for my_ip_cidr. Access goes through Jenkins:
#   SSH: ProxyJump via Jenkins
#   UI:  ssh -L 9000:<sonar_private_ip>:9000 ec2-user@<jenkins_eip>

resource "aws_security_group_rule" "sonar_ingress_jenkins_ssh" {
  description              = "Ansible ProxyJump - SSH from Jenkins to SonarQube in private subnet"
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.jenkins.id
  security_group_id        = aws_security_group.sonar.id
}

resource "aws_security_group_rule" "sonar_ingress_jenkins_9000" {
  description              = "Jenkins pipeline - SonarQube scanner and Quality Gate webhook"
  type                     = "ingress"
  from_port                = 9000
  to_port                  = 9000
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.jenkins.id
  security_group_id        = aws_security_group.sonar.id
}

resource "aws_security_group_rule" "sonar_egress_all" {
  description       = "Allow all outbound - SonarQube pulls plugins, sends webhook to Jenkins"
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.sonar.id
}
