# Dwellio DEV root module — wires shared modules (Task 44+).

locals {
  tags = {
    Project     = "dwellio"
    Environment = var.environment
  }
}

module "vpc" {
  source = "../../modules/vpc"

  name_prefix = var.name_prefix
  vpc_cidr    = var.vpc_cidr
  api_port    = var.api_port
  tags        = local.tags
}

module "alb" {
  source = "../../modules/alb"

  name_prefix           = var.name_prefix
  vpc_id                = module.vpc.vpc_id
  public_subnet_ids     = module.vpc.public_subnet_ids
  alb_security_group_id = module.vpc.alb_security_group_id
  api_port              = var.api_port
  api_hostname          = var.api_hostname
  hosted_zone_name      = var.hosted_zone_name
  tags                  = local.tags
}

module "ecs" {
  source = "../../modules/ecs"

  name_prefix              = var.name_prefix
  aws_region               = var.aws_region
  app_subnet_ids           = module.vpc.app_subnet_ids
  api_security_group_id    = module.vpc.api_security_group_id
  worker_security_group_id = module.vpc.worker_security_group_id
  target_group_arn         = module.alb.target_group_arn
  api_port                 = var.api_port
  task_cpu                 = var.task_cpu
  task_memory              = var.task_memory
  api_desired_count        = var.api_desired_count
  billing_desired_count    = var.billing_desired_count
  invoice_desired_count    = var.invoice_desired_count
  tags                     = local.tags
}

module "aurora" {
  source = "../../modules/aurora"

  name_prefix          = var.name_prefix
  db_subnet_ids        = module.vpc.db_subnet_ids
  db_security_group_id = module.vpc.db_security_group_id
  min_acu              = var.aurora_min_acu
  max_acu              = var.aurora_max_acu
  deletion_protection  = var.aurora_deletion_protection
  tags                 = local.tags
}

module "s3" {
  source = "../../modules/s3"

  name_prefix          = var.name_prefix
  cors_allowed_origins = var.document_cors_origins
  tags                 = local.tags
}

module "sqs" {
  source = "../../modules/sqs"

  name_prefix             = var.name_prefix
  enable_billing_schedule = true
  tags                    = local.tags
}

module "iam" {
  source = "../../modules/iam"

  name_prefix                 = var.name_prefix
  aws_region                  = var.aws_region
  github_repository           = var.github_repository
  create_github_oidc_provider = var.create_github_oidc_provider
  api_ecr_repository_arn      = module.ecs.api_ecr_repository_arn
  worker_ecr_repository_arn   = module.ecs.worker_ecr_repository_arn
  ecs_execution_role_arn      = module.ecs.execution_role_arn
  ecs_task_role_arn           = module.ecs.task_role_arn
  tags                        = local.tags
}

module "monitoring" {
  source = "../../modules/monitoring"

  name_prefix               = var.name_prefix
  alb_arn_suffix            = module.alb.alb_arn_suffix
  ecs_cluster_name          = module.ecs.cluster_name
  aurora_cluster_identifier = module.aurora.cluster_id
  tags                      = local.tags
}

# App config secret stub — values set out-of-band (never commit secrets).
resource "aws_secretsmanager_secret" "app" {
  name                    = "${var.name_prefix}/app"
  description             = "Dwellio app config stub (Auth0, Razorpay, etc.) — fill via console/CLI"
  recovery_window_in_days = 0

  tags = merge(local.tags, { Name = "${var.name_prefix}-app-secret" })
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    AUTH0_ISSUER_URI = ""
    AUTH0_AUDIENCE   = ""
    note             = "Replace empty values out-of-band; do not store production secrets in git"
  })
}
