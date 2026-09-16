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

variable "github_repository" {
  type        = string
  description = "Human-readable GitHub org/repo (docs only; not used for IAM sub trust)"
}

variable "github_oidc_sub_prefix" {
  type        = string
  description = "GitHub OIDC sub prefix before context (e.g. repo:org@id/repo@id). Module appends :*"
}

variable "create_github_oidc_provider" {
  type        = bool
  description = "Create the account-level GitHub OIDC provider (true once per account, typically DEV)"
  default     = true
}

variable "api_ecr_repository_arn" {
  type = string
}

variable "worker_ecr_repository_arn" {
  type = string
}

variable "ecs_execution_role_arn" {
  type = string
}

variable "ecs_task_role_arn" {
  type = string
}
