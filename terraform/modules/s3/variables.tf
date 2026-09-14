variable "name_prefix" {
  type        = string
  description = "Resource name prefix, e.g. dwellio-dev"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module"
  default     = {}
}

variable "cors_allowed_origins" {
  type        = list(string)
  description = "Browser origins allowed for document uploads (presigned POST/PUT)"
}
