environment                 = "dev"
name_prefix                 = "dwellio-dev"
vpc_cidr                    = "10.10.0.0/16"
api_hostname                = "api-dev.vikranthreddy.com"
hosted_zone_name            = "vikranthreddy.com"
aws_region                  = "ap-south-1"
github_repository           = "supreme-reality/dwellio"
github_oidc_sub_prefix      = "repo:supreme-reality@73769022/dwellio@1377241890"
create_github_oidc_provider = true
aurora_min_acu              = 0
aurora_max_acu              = 2
aurora_deletion_protection  = false
task_cpu                    = "256"
task_memory                 = "512"
api_desired_count           = 1
billing_desired_count       = 0
invoice_desired_count       = 0
document_cors_origins = [
  "http://localhost:3000",
  "https://app.vikranthreddy.com",
]
