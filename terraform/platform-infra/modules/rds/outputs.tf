output "rds_endpoint" {
  description = "RDS connection endpoint (host:port) — use as SPRING_DATASOURCE_URL host"
  value       = aws_db_instance.postgres.endpoint
}