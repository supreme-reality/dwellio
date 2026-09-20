variable "name_prefix" {
  type        = string
  description = "Resource name prefix, e.g. dwellio-dev"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module"
  default     = {}
}

variable "db_subnet_ids" {
  type = list(string)
}

variable "db_security_group_id" {
  type = string
}

variable "min_acu" {
  type        = number
  description = "Aurora Serverless v2 minimum ACU"
}

variable "max_acu" {
  type        = number
  description = "Aurora Serverless v2 maximum ACU"
}

variable "database_name" {
  type    = string
  default = "dwellio"
}

variable "master_username" {
  type    = string
  default = "dwellio"
}

variable "engine_version" {
  type        = string
  description = "Aurora PostgreSQL engine version"
  default     = "15.19"
}

variable "deletion_protection" {
  type        = bool
  description = "Enable RDS deletion protection (recommended for PROD)"
  default     = false
}
