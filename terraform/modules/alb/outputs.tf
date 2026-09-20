output "alb_arn" {
  value = aws_lb.api.arn
}

output "alb_arn_suffix" {
  value = aws_lb.api.arn_suffix
}

output "alb_dns_name" {
  value = aws_lb.api.dns_name
}

output "target_group_arn" {
  value = aws_lb_target_group.api.arn
}

output "certificate_arn" {
  value = aws_acm_certificate_validation.api.certificate_arn
}

output "api_url" {
  value = "https://${var.api_hostname}"
}
