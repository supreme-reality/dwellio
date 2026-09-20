variable "name_prefix" {
  type        = string
  description = "Resource name prefix, e.g. dwellio-dev"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module"
  default     = {}
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "api_port" {
  type    = number
  default = 8080
}

variable "api_hostname" {
  type        = string
  description = "Public API hostname for ACM + Route 53"
}

variable "hosted_zone_name" {
  type        = string
  description = "Route 53 hosted zone name"
}
