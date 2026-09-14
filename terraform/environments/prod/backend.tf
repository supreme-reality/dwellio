# Local state until the S3 state bucket is bootstrapped (see ../../README.md).
# After bootstrap, replace this file with the S3 backend from backend.s3.example.tf
# and run: terraform init -migrate-state
terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}
