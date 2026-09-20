# Dwellio module: iam
# GitHub OIDC provider + roles for Terraform plan/apply and ECR/ECS deploy.
# Trust limited via github_oidc_sub_prefix (ID-qualified GitHub OIDC sub).

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  # Matches any context (ref / environment) under the trusted repo identity.
  oidc_sub_repo = "${var.github_oidc_sub_prefix}:*"
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["ffffffffffffffffffffffffffffffffffffffff"]

  tags = merge(var.tags, { Name = "github-actions-oidc" })
}

locals {
  # Construct ARN when not creating (PROD). Avoids data-source lookup so `plan` works
  # before the provider exists; DEV must create the provider first (or apply order DEV → PROD).
  github_oidc_provider_arn = (
    var.create_github_oidc_provider
    ? aws_iam_openid_connect_provider.github[0].arn
    : "arn:aws:iam::${local.account_id}:oidc-provider/token.actions.githubusercontent.com"
  )
}

data "aws_iam_policy_document" "gha_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.oidc_sub_repo]
    }
  }
}

resource "aws_iam_role" "terraform" {
  name               = "${var.name_prefix}-gha-terraform"
  assume_role_policy = data.aws_iam_policy_document.gha_assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-gha-terraform" })
}

# Broad enough for Capstone MVP Terraform apply; tighten before multi-tenant prod.
resource "aws_iam_role_policy_attachment" "terraform_admin" {
  role       = aws_iam_role.terraform.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

resource "aws_iam_role" "deploy" {
  name               = "${var.name_prefix}-gha-deploy"
  assume_role_policy = data.aws_iam_policy_document.gha_assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-gha-deploy" })
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid = "EcrAuth"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeRepositories",
      "ecr:ListImages",
    ]
    resources = [
      var.api_ecr_repository_arn,
      var.worker_ecr_repository_arn,
    ]
  }

  statement {
    sid = "EcsDeploy"
    actions = [
      "ecs:DescribeClusters",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
      "ecs:UpdateService",
      "ecs:ListTasks",
      "ecs:DescribeTasks",
    ]
    resources = ["*"]
  }

  statement {
    sid = "PassEcsRoles"
    actions = [
      "iam:PassRole",
    ]
    resources = [
      var.ecs_execution_role_arn,
      var.ecs_task_role_arn,
    ]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "dwellio-gha-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
