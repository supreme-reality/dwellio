provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "dwellio"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
