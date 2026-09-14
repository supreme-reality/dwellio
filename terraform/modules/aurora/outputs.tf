output "cluster_endpoint" {
  value = aws_rds_cluster.this.endpoint
}

output "cluster_reader_endpoint" {
  value = aws_rds_cluster.this.reader_endpoint
}

output "cluster_id" {
  value = aws_rds_cluster.this.id
}

output "master_user_secret_arn" {
  description = "RDS-managed master user secret ARN"
  value       = try(aws_rds_cluster.this.master_user_secret[0].secret_arn, null)
}

output "app_db_secret_arn" {
  value = aws_secretsmanager_secret.app_db.arn
}
