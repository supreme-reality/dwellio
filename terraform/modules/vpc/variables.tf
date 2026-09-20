variable "name_prefix" {
  type        = string
  description = "Resource name prefix, e.g. dwellio-dev"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module"
  default     = {}
}

variable "vpc_cidr" {
  type        = string
  description = "VPC IPv4 CIDR"
}

variable "api_port" {
  type        = number
  description = "API container/listen port (ALB -> API SG)"
  default     = 8080
}
