resource "aws_iam_instance_profile" "jenkins_profile" {
  name = "${var.name}-jenkins-instance-profile"
  role = aws_iam_role.jenkins_ec2_role.name
}

resource "aws_instance" "jenkins_catalogix" {
  ami                    = var.ami
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public[0].id
  key_name               = var.key_name
  iam_instance_profile   = aws_iam_instance_profile.jenkins_profile.name
  vpc_security_group_ids = [aws_security_group.jenkins.id]

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted = true
  }

  metadata_options {
    http_endpoint = "enabled"
    # IMDSv2 — prevents SSRF attacks from reaching the metadata service
    http_tokens   = "required"
  }

  tags = {
    Name = "Jenkins Server - Catalogix"
    Project = var.project_tag
    Role = "ci-cd"
  }
}

resource "aws_iam_instance_profile" "sonar_profile" {
  name = "${var.name}-sonar-instance-profile"
  role = aws_iam_role.sonar_ec2_role.name
}

resource "aws_instance" "sonarqube_catalogix" {
  ami                    = var.ami
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.private[0].id
  key_name               = var.key_name
  iam_instance_profile   = aws_iam_instance_profile.sonar_profile.name
  vpc_security_group_ids = [aws_security_group.sonar.id]

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = {
    Name = "SonarQube Server - Catalogix" 
    Project = var.project_tag
    Role = "code-quality"
  }
}