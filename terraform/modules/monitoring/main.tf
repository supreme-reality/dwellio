# Dwellio module: monitoring
# CloudWatch alarms (console only — no SNS in MVP).

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.name_prefix}-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_description   = "ALB target 5xx > 5 in 1 minute (console only; no SNS)"

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_api_cpu" {
  alarm_name          = "${var.name_prefix}-api-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 85
  treat_missing_data  = "notBreaching"
  alarm_description   = "API service CPU > 85%"

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = "api"
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "aurora_acu" {
  alarm_name          = "${var.name_prefix}-aurora-acu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ServerlessDatabaseCapacity"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 3
  treat_missing_data  = "notBreaching"
  alarm_description   = "Aurora Serverless capacity elevated (tune per env)"

  dimensions = {
    DBClusterIdentifier = var.aurora_cluster_identifier
  }

  tags = var.tags
}
