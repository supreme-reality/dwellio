# Remote state: S3 + native lockfile (bucket created one-time via AWS CLI).
# Bucket: dwellio-terraform-state
# Key:    dwellio/terraform/dev/terraform.tfstate
terraform {
  backend "s3" {
    bucket       = "dwellio-terraform-state"
    key          = "dwellio/terraform/dev/terraform.tfstate"
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }
}
