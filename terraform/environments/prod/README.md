# PROD environment

| Setting | Value (Infra v5) |
|---|---|
| VPC | `10.20.0.0/16` |
| API hostname | `api.vikranthreddy.com` |
| Aurora ACU | 0.5–4 |
| Task size | 0.5 vCPU / 1 GB (`512` / `1024`) |
| API desired | 2 |
| Billing desired | 0 |
| Invoice desired | 1 |
| OIDC provider create | `false` (DEV creates account-level provider) |
| Aurora deletion protection | `true` |

## Apply policy

- **Plan-only** in Tasks 42–47 until you explicitly approve PROD apply.
- Use GitHub Environment `production` (required reviewers) for `workflow_dispatch` apply.
- Apply **DEV first** so the GitHub OIDC provider exists.

## Sleep / wake

PROD sleep **requires confirmation** (accepted downtime). Do not script unattended scale-to-zero.

```bash
# Only after explicit approval:
aws ecs update-service --cluster dwellio-prod --service api --desired-count 0 --region ap-south-1
aws ecs update-service --cluster dwellio-prod --service billing --desired-count 0 --region ap-south-1
aws ecs update-service --cluster dwellio-prod --service invoice --desired-count 0 --region ap-south-1
```

Wake: restore desired counts from `terraform.tfvars` (API 2, billing 0, invoice 1).

## Commands

```bash
cd terraform/environments/prod
terraform init
terraform plan
# terraform apply   # gated — human approval required
# terraform destroy # tears down PROD only
```
