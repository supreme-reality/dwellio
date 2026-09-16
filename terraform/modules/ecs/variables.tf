variable "name_prefix" {
  type        = string
  description = "Resource name prefix, e.g. dwellio-dev"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module"
  default     = {}
}

variable "aws_region" {
  type = string
}

variable "app_subnet_ids" {
  type = list(string)
}

variable "api_security_group_id" {
  type = string
}

variable "worker_security_group_id" {
  type = string
}

variable "target_group_arn" {
  type        = string
  description = "ALB target group for the API service"
}

variable "api_port" {
  type    = number
  default = 8080
}

variable "task_cpu" {
  type        = string
  description = "Fargate CPU units (256 = 0.25 vCPU)"
}

variable "task_memory" {
  type        = string
  description = "Fargate memory MiB"
}

variable "api_desired_count" {
  type = number
}

variable "billing_desired_count" {
  type = number
}

variable "invoice_desired_count" {
  type = number
}

variable "api_image_tag" {
  type    = string
  default = "latest"
}

variable "worker_image_tag" {
  type    = string
  default = "latest"
}

variable "database_jdbc_url" {
  type        = string
  description = "JDBC URL for API/worker (Aurora), e.g. jdbc:postgresql://host:5432/dwellio"
}

variable "db_secret_arn" {
  type        = string
  description = "App DB secret ARN (JSON keys: username, ...)"
}

variable "db_master_secret_arn" {
  type        = string
  description = "RDS-managed master user secret ARN (JSON key: password)"
}

variable "app_secret_arn" {
  type        = string
  description = "App config secret ARN (JSON keys: AUTH0_ISSUER_URI, AUTH0_AUDIENCE, ...)"
}

variable "documents_bucket" {
  type        = string
  description = "S3 documents bucket name"
}
