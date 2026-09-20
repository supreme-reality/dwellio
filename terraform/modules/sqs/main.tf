# Dwellio module: sqs
# Billing trigger + invoice queues and DLQs; EventBridge schedule 1st 23:00 IST -> billing.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

resource "aws_sqs_queue" "billing_dlq" {
  name                      = "${var.name_prefix}-billing-trigger-dlq"
  message_retention_seconds = 1209600 # 14 days

  tags = merge(var.tags, { Name = "${var.name_prefix}-billing-trigger-dlq" })
}

resource "aws_sqs_queue" "billing" {
  name                       = "${var.name_prefix}-billing-trigger"
  visibility_timeout_seconds = 300    # 5 min
  receive_wait_time_seconds  = 20     # long poll
  message_retention_seconds  = 345600 # 4 days
  max_message_size           = 262144

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.billing_dlq.arn
    maxReceiveCount     = 3
  })

  tags = merge(var.tags, { Name = "${var.name_prefix}-billing-trigger" })
}

resource "aws_sqs_queue" "invoice_dlq" {
  name                      = "${var.name_prefix}-invoice-dlq"
  message_retention_seconds = 1209600

  tags = merge(var.tags, { Name = "${var.name_prefix}-invoice-dlq" })
}

resource "aws_sqs_queue" "invoice" {
  name                       = "${var.name_prefix}-invoice"
  visibility_timeout_seconds = 600 # 10 min
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 604800 # 7 days

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.invoice_dlq.arn
    maxReceiveCount     = 3
  })

  tags = merge(var.tags, { Name = "${var.name_prefix}-invoice" })
}

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "billing_scheduler" {
  count = var.enable_billing_schedule ? 1 : 0

  name               = "${var.name_prefix}-billing-scheduler"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
  tags               = var.tags
}

data "aws_iam_policy_document" "scheduler_sqs" {
  count = var.enable_billing_schedule ? 1 : 0

  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.billing.arn]
  }
}

resource "aws_iam_role_policy" "billing_scheduler" {
  count = var.enable_billing_schedule ? 1 : 0

  name   = "send-billing-trigger"
  role   = aws_iam_role.billing_scheduler[0].id
  policy = data.aws_iam_policy_document.scheduler_sqs[0].json
}

resource "aws_scheduler_schedule" "billing_monthly" {
  count = var.enable_billing_schedule ? 1 : 0

  name                         = "${var.name_prefix}-billing-monthly"
  description                  = "1st of month 23:00 IST -> billing trigger SQS"
  schedule_expression          = "cron(0 23 1 * ? *)"
  schedule_expression_timezone = "Asia/Kolkata"
  state                        = "ENABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_sqs_queue.billing.arn
    role_arn = aws_iam_role.billing_scheduler[0].arn
    input = jsonencode({
      type     = "MONTHLY_BILLING_TRIGGER"
      source   = "eventbridge-scheduler"
      timezone = "Asia/Kolkata"
    })
  }
}
