# Dwellio module: ecs
# ECR repos, ECS cluster, api + billing + invoice Fargate services.
# lifecycle.ignore_changes on desired_count for sleep/wake.
# WORKER_ROLE selects billing vs invoice consumers on the shared worker image.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  account_id       = data.aws_caller_identity.current.account_id
  api_image        = "${aws_ecr_repository.api.repository_url}:${var.api_image_tag}"
  worker_image     = "${aws_ecr_repository.worker.repository_url}:${var.worker_image_tag}"
  api_log_group    = "/dwellio/${var.name_prefix}/api"
  worker_log_group = "/dwellio/${var.name_prefix}/worker"
}

resource "aws_ecr_repository" "api" {
  name                 = "${var.name_prefix}-api"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-api-ecr" })
}

resource "aws_ecr_repository" "worker" {
  name                 = "${var.name_prefix}-worker"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-worker-ecr" })
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "worker" {
  repository = aws_ecr_repository.worker.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "api" {
  name              = local.api_log_group
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = local.worker_log_group
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_ecs_cluster" "this" {
  name = var.name_prefix

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = merge(var.tags, { Name = var.name_prefix })
}

data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Pull Secrets Manager values into task env (incl. RDS-managed master secret + KMS).
data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid = "SecretsInject"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      var.db_secret_arn,
      var.db_master_secret_arn,
      var.app_secret_arn,
    ]
  }

  statement {
    sid = "KmsDecryptForSecrets"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${var.aws_region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "dwellio-execution-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
  tags               = var.tags
}

# Task role: SQS, S3, Secrets (scoped loosely for MVP; tighten later).
data "aws_iam_policy_document" "task" {
  statement {
    sid = "SqsConsume"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:ChangeMessageVisibility",
      "sqs:SendMessage",
    ]
    resources = ["arn:aws:sqs:${var.aws_region}:${local.account_id}:${var.name_prefix}-*"]
  }

  statement {
    sid = "S3Documents"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
      "s3:HeadObject",
    ]
    resources = [
      "arn:aws:s3:::${var.name_prefix}-documents-${local.account_id}",
      "arn:aws:s3:::${var.name_prefix}-documents-${local.account_id}/*",
    ]
  }

  statement {
    sid = "SecretsRead"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = ["arn:aws:secretsmanager:${var.aws_region}:${local.account_id}:secret:${var.name_prefix}/*"]
  }
}

resource "aws_iam_role_policy" "task" {
  name   = "dwellio-task"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = local.api_image
      essential = true
      portMappings = [
        {
          containerPort = var.api_port
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "SERVER_PORT", value = tostring(var.api_port) },
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "SPRING_DATASOURCE_URL", value = var.database_jdbc_url },
        { name = "DWELLIO_STORAGE_TYPE", value = "s3" },
        { name = "DWELLIO_DOCUMENTS_BUCKET", value = var.documents_bucket },
        { name = "DWELLIO_S3_REGION", value = var.aws_region },
        # Empty → real AWS S3 + task-role credentials (see StorageConfig).
        { name = "DWELLIO_S3_ENDPOINT", value = "" },
        { name = "DWELLIO_S3_ACCESS_KEY", value = "" },
        { name = "DWELLIO_S3_SECRET_KEY", value = "" },
        { name = "DWELLIO_S3_PATH_STYLE", value = "false" },
      ]
      secrets = [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${var.db_secret_arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${var.db_master_secret_arn}:password::"
        },
        {
          name      = "AUTH0_ISSUER_URI"
          valueFrom = "${var.app_secret_arn}:AUTH0_ISSUER_URI::"
        },
        {
          name      = "AUTH0_AUDIENCE"
          valueFrom = "${var.app_secret_arn}:AUTH0_AUDIENCE::"
        },
        {
          name      = "RAZORPAY_KEY_ID"
          valueFrom = "${var.app_secret_arn}:RAZORPAY_KEY_ID::"
        },
        {
          name      = "RAZORPAY_KEY_SECRET"
          valueFrom = "${var.app_secret_arn}:RAZORPAY_KEY_SECRET::"
        },
        {
          name      = "RAZORPAY_WEBHOOK_SECRET"
          valueFrom = "${var.app_secret_arn}:RAZORPAY_WEBHOOK_SECRET::"
        },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = local.api_log_group
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "api"
        }
      }
    }
  ])

  tags = var.tags
}

resource "aws_ecs_task_definition" "billing" {
  family                   = "${var.name_prefix}-billing"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = local.worker_image
      essential = true
      environment = [
        { name = "WORKER_ROLE", value = "billing" },
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "SPRING_DATASOURCE_URL", value = var.database_jdbc_url },
      ]
      secrets = [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${var.db_secret_arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${var.db_master_secret_arn}:password::"
        },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = local.worker_log_group
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "billing"
        }
      }
    }
  ])

  tags = var.tags
}

resource "aws_ecs_task_definition" "invoice" {
  family                   = "${var.name_prefix}-invoice"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = local.worker_image
      essential = true
      environment = [
        { name = "WORKER_ROLE", value = "invoice" },
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "SPRING_DATASOURCE_URL", value = var.database_jdbc_url },
      ]
      secrets = [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${var.db_secret_arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${var.db_master_secret_arn}:password::"
        },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = local.worker_log_group
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "invoice"
        }
      }
    }
  ])

  tags = var.tags
}

resource "aws_ecs_service" "api" {
  name            = "api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count
  launch_type     = "FARGATE"

  # Aurora Serverless + Spring Boot cold start can exceed 1–2 minutes.
  health_check_grace_period_seconds = 300

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [var.api_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "api"
    container_port   = var.api_port
  }

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [desired_count]
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.api]
}

resource "aws_ecs_service" "billing" {
  name            = "billing"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.billing.arn
  desired_count   = var.billing_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [var.worker_security_group_id]
    assign_public_ip = false
  }

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  lifecycle {
    ignore_changes = [desired_count]
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.worker]
}

resource "aws_ecs_service" "invoice" {
  name            = "invoice"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.invoice.arn
  desired_count   = var.invoice_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [var.worker_security_group_id]
    assign_public_ip = false
  }

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [desired_count]
  }

  tags = var.tags

  depends_on = [aws_cloudwatch_log_group.worker]
}
