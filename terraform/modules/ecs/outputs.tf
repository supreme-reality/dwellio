output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  value = aws_ecs_cluster.this.arn
}

output "api_service_name" {
  value = aws_ecs_service.api.name
}

output "billing_service_name" {
  value = aws_ecs_service.billing.name
}

output "invoice_service_name" {
  value = aws_ecs_service.invoice.name
}

output "api_ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "worker_ecr_repository_url" {
  value = aws_ecr_repository.worker.repository_url
}

output "api_ecr_repository_arn" {
  value = aws_ecr_repository.api.arn
}

output "worker_ecr_repository_arn" {
  value = aws_ecr_repository.worker.arn
}

output "execution_role_arn" {
  value = aws_iam_role.execution.arn
}

output "task_role_arn" {
  value = aws_iam_role.task.arn
}
