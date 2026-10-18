# Uses .address (hostname only) rather than .endpoint (host:port), because the
# Helm chart builds the JDBC URL as jdbc:postgresql://<host>:<port>/<db> and a
# host:port value would duplicate the port.

output "rds_address" {
  description = "RDS hostname (no port) — use as database.host in Helm values"
  value       = aws_db_instance.postgres.address
}

output "rds_endpoint" {
  description = "RDS endpoint (host:port) — use as database.endpoint in Helm values"
  value       = aws_db_instance.postgres.endpoint
}

output "ssm_parameter_path" {
  description = "SSM Parameter Store path where the RDS endpoint was published"
  value       = aws_ssm_parameter.rds_endpoint.name
}