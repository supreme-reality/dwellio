output "github_oidc_provider_arn" {
  value = local.github_oidc_provider_arn
}

output "terraform_role_arn" {
  value = aws_iam_role.terraform.arn
}

output "deploy_role_arn" {
  value = aws_iam_role.deploy.arn
}

output "oidc_sub_condition" {
  description = "IAM trust StringLike value for GitHub sub claim"
  value       = local.oidc_sub_repo
}
