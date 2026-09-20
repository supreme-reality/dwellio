variable "aws_region" {
  type        = string
  description = "AWS region"
  default     = "ap-south-1"
}

variable "environment" {
  type        = string
  description = "Environment name (dev|prod)"
}

variable "name_prefix" {
  type        = string
  description = "Prefix for resource names"
}

variable "vpc_cidr" {
  type        = string
  description = "VPC IPv4 CIDR"
}

variable "api_hostname" {
  type        = string
  description = "Public API hostname (ACM + Route 53)"
}

variable "hosted_zone_name" {
  type        = string
  description = "Route 53 hosted zone name"
  default     = "vikranthreddy.com"
}

variable "github_repository" {
  type        = string
  description = "Human-readable GitHub org/repo (docs only)"
  default     = "supreme-reality/dwellio"
}

variable "github_oidc_sub_prefix" {
  type        = string
  description = "GitHub OIDC sub prefix before context (e.g. repo:org@id/repo@id). Module appends :*"
}

variable "create_github_oidc_provider" {
  type        = bool
  description = "Create account-level GitHub OIDC provider (DEV only typically)"
  default     = false
}

variable "api_port" {
  type    = number
  default = 8080
}

variable "aurora_min_acu" {
  type = number
}

variable "aurora_max_acu" {
  type = number
}

variable "aurora_deletion_protection" {
  type    = bool
  default = false
}

variable "document_cors_origins" {
  type        = list(string)
  description = "Allowed CORS origins for the documents bucket"
}

variable "task_cpu" {
  type = string
}

variable "task_memory" {
  type = string
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
  type        = string
  description = "ECR tag for API service image"
  default     = "latest"
}
