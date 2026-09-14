output "alb_5xx_alarm_name" {
  value = aws_cloudwatch_metric_alarm.alb_5xx.alarm_name
}

output "api_cpu_alarm_name" {
  value = aws_cloudwatch_metric_alarm.ecs_api_cpu.alarm_name
}
