output "instance_id_jenkins" {
  description = "Instance ID of the Jenkins Server — used by bootstrap script to wait for healthy status"
  value       = aws_instance.jenkins_catalogix.id
}

output "instance_id_sonarqube" {
  description = "Instance ID of the SonarQube Server — used by bootstrap script to wait for healthy status"
  value       = aws_instance.sonarqube_catalogix.id
}

output "public_ip_jenkins" {
  description = "Public IP of the Jenkins Server — open http://<ip>:8080 in browser"
  value       = aws_instance.jenkins_catalogix.public_ip
}

output "private_ip_sonarqube" {
  description = "Private IP of SonarQube — access via SSH tunnel: ssh -L 9000:<private_ip>:9000 ec2-user@<jenkins_public_ip>"
  value       = aws_instance.sonarqube_catalogix.private_ip
}

output "jenkins_sg_id" {
  description = "Jenkins SG ID — consumed by platform-infra to allow kubectl access to EKS private endpoint"
  value       = aws_security_group.jenkins.id
}

output "vpc_id" {
  description = "VPC ID — consumed by platform-infra via remote state"
  value       = aws_vpc.this.id
}

output "public_subnets" {
  description = "Public Subnets of VPC — consumed by platform-infra security groups"
  value       = aws_subnet.public[*].id
}

output "private_subnets" {
  description = "Private Subnets of VPC — consumed by platform-infra security groups"
  value       = aws_subnet.private[*].id
}

output "vpc_cidr" {
  description = "VPC CIDR block — consumed by platform-infra security groups"
  value       = aws_vpc.this.cidr_block
}