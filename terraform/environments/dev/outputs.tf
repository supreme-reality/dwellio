output "vpc_id" {
  value = module.vpc.vpc_id
}

output "api_url" {
  value = module.alb.api_url
}

output "alb_dns_name" {
  value = module.alb.alb_dns_name
}

output "aurora_endpoint" {
  value = module.aurora.cluster_endpoint
}

output "documents_bucket_id" {
  value = module.s3.documents_bucket_id
}

output "billing_queue_url" {
  value = module.sqs.billing_queue_url
}

output "invoice_queue_url" {
  value = module.sqs.invoice_queue_url
}

output "app_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "app_db_secret_arn" {
  value = module.aurora.app_db_secret_arn
}

output "ecs_cluster_name" {
  value = module.ecs.cluster_name
}

output "api_ecr_repository_url" {
  value = module.ecs.api_ecr_repository_url
}

output "worker_ecr_repository_url" {
  value = module.ecs.worker_ecr_repository_url
}

output "gha_terraform_role_arn" {
  value = module.iam.terraform_role_arn
}

output "gha_deploy_role_arn" {
  value = module.iam.deploy_role_arn
}

output "oidc_sub_condition" {
  value = module.iam.oidc_sub_condition
}
