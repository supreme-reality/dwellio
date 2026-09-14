variable "name_prefix" {
  type        = string
  description = "Resource name prefix, e.g. dwellio-dev"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module"
  default     = {}
}

variable "enable_billing_schedule" {
  type        = bool
  description = "Create EventBridge schedule (1st of month 23:00 IST -> billing queue)"
  default     = true
}
