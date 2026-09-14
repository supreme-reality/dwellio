output "billing_queue_url" {
  value = aws_sqs_queue.billing.url
}

output "billing_queue_arn" {
  value = aws_sqs_queue.billing.arn
}

output "invoice_queue_url" {
  value = aws_sqs_queue.invoice.url
}

output "invoice_queue_arn" {
  value = aws_sqs_queue.invoice.arn
}

output "billing_dlq_arn" {
  value = aws_sqs_queue.billing_dlq.arn
}

output "invoice_dlq_arn" {
  value = aws_sqs_queue.invoice_dlq.arn
}
