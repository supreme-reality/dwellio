environment                 = "prod"
name_prefix                 = "dwellio-prod"
vpc_cidr                    = "10.20.0.0/16"
api_hostname                = "api.vikranthreddy.com"
hosted_zone_name            = "vikranthreddy.com"
aws_region                  = "ap-south-1"
github_repository           = "supreme-reality/dwellio"
github_oidc_sub_prefix      = "repo:supreme-reality@73769022/dwellio@1377241890"
create_github_oidc_provider = false
aurora_min_acu              = 0.5
aurora_max_acu              = 4
aurora_deletion_protection  = true
task_cpu                    = "512"
task_memory                 = "1024"
api_desired_count           = 2
billing_desired_count       = 0
invoice_desired_count       = 1
document_cors_origins = [
  "https://app.vikranthreddy.com",
]
