# Dwellio Terraform

AWS infrastructure for Dwellio (`ap-south-1`). Layout matches Terraform v5.0:

```
terraform/
  modules/          # vpc, alb, ecs, aurora, s3, sqs, iam, monitoring
  environments/     # dev, prod
```

See `docs/infrastructure-runbook.md` and `docs/superpowers/specs/2026-09-19-dwellio-infra-terraform-ci-design.md`.

## State backend bootstrap (one-time)

Environments start with a **local** backend (`backend.tf`) so `validate` / `plan` work before the bucket exists.

Remote state (recommended for CI / shared use): **S3 + S3 native lockfile** (no DynamoDB).

1. Create a dedicated private bucket (example name `dwellio-terraform-state`) in `ap-south-1`.
2. Enable **versioning** and **default encryption** (SSE-S3 or SSE-KMS).
3. Block all public access.
4. Copy `environments/<env>/backend.s3.tf.example` over `backend.tf` (adjust bucket name if needed).
5. Keys:
   - DEV: `dwellio/terraform/dev/terraform.tfstate`
   - PROD: `dwellio/terraform/prod/terraform.tfstate`
6. Run `terraform init -migrate-state` in that environment.

Example (AWS CLI; run once with an admin principal):

```bash
aws s3api create-bucket \
  --bucket dwellio-terraform-state \
  --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1

aws s3api put-bucket-versioning \
  --bucket dwellio-terraform-state \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket dwellio-terraform-state \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-public-access-block \
  --bucket dwellio-terraform-state \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

Then:

```bash
cd environments/dev
cp backend.s3.tf.example backend.tf   # after editing bucket name if needed
terraform init -migrate-state
terraform plan
```

## Environments

| Env | CIDR | API hostname |
|---|---|---|
| `dev` | 10.10.0.0/16 | api-dev.vikranthreddy.com |
| `prod` | 10.20.0.0/16 | api.vikranthreddy.com |

```bash
cd environments/dev   # or prod
terraform plan
terraform apply       # DEV only with human approval; PROD gated separately
terraform destroy     # this environment only
```

## Modules (fold-ins)

| Module | Also owns (no extra top-level module) |
|---|---|
| `alb` | ACM + Route 53 API alias |
| `ecs` | ECR; `ignore_changes` on `desired_count` |
| `sqs` | EventBridge schedule → billing queue |
| `iam` | GitHub OIDC (`supreme-reality/dwellio`) |
