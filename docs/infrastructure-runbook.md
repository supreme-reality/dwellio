# Dwellio infrastructure runbook

Operator reference for AWS environments. Product brand is **Dwellio**. Domain specs live under `projectDocs/v5/` (StayFlow-named docs).

**Sources of truth**

| Doc | Role |
|---|---|
| `projectDocs/v5/StayFlow_Infrastructure_v5.0.md` | Capacity, network, SQS, S3, sleep |
| `projectDocs/v5/StayFlow_Terraform_v5.0.md` | Module layout, CI, capacity variables |
| `docs/superpowers/specs/2026-09-19-dwellio-infra-terraform-ci-design.md` | Locked Dwellio decisions (region, DNS, OIDC, apply policy) |

**Region:** `ap-south-1` (Mumbai)  
**Accounts:** one AWS account; DEV and PROD are separate Terraform environments (separate VPCs and state keys).

---

## 1. Infra v5 checklist (1:1)

Use this as the acceptance map for Tasks 42–47.

| Infra v5 item | Runbook section | Notes |
|---|---|---|
| VPC / ALB / ECS / Aurora / SQS / S3 / Secrets / OIDC / CloudWatch / SSM | §§2–8 | Unchanged topology from v1.3 / v5 alignment |
| Billing schedule EventBridge 23:00 IST on the 1st | §5 | Implemented in Terraform (`sqs` module fold-in) |
| Document upload HeadObject → `UPLOADED` | §6 | App logic; bucket rules in Terraform |
| No new services for tickets / bed block / tenant org scope | §12 | Application/DB only |
| Capacity table (VPC, AZs, ALB, ECS, Aurora, NAT, SQS) | §2 | |
| Task sizing DEV/PROD | §2 | |
| Two worker ECS services, one worker image, `WORKER_ROLE` | §2, §7 | |
| Network & SGs | §3 | |
| Health `/actuator/health` timings | §3 | |
| SQS settings + DLQs | §5 | |
| S3 private / BPA / encryption / CORS / multipart abort | §6 | |
| Sleep / wake; ignore `desired_count`; no deep sleep | §9 | |
| Scope boundary | §12 | |

---

## 2. Capacity baseline

| Component | DEV | PROD |
|---|---|---|
| VPC CIDR | `10.10.0.0/16` | `10.20.0.0/16` |
| AZs | 2 | 2 |
| ALB | 1 | 1 |
| API ECS (min / desired / max) | 0 / 1 / 2 | 2 / 2 / 4 |
| Billing worker | 0 / 0 / 1 | 0 / 0 / 1 |
| Invoice worker | 0 / 0 / 1 | 1 / 1 / 5 |
| Aurora ACU | 0–2 | 0.5–4 |
| NAT | 1 initially | 1 initially (2 for strict AZ HA later) |
| SQS | 2 queues + 2 DLQs | 2 queues + 2 DLQs |

**Task sizing**

| Env | vCPU | Memory |
|---|---|---|
| DEV | 0.25 | 512 MB |
| PROD | 0.5 | 1 GB |

Billing and Invoice are **two ECS services** sharing **one worker ECR image**, selected by `WORKER_ROLE=billing` or `WORKER_ROLE=invoice` (`apps/worker`).

---

## 3. Network & security

- Public subnets: ALB only.
- Private app subnets: ECS tasks (API + workers). No public IPs on tasks.
- Private DB subnets: Aurora.
- **ALB SG:** inbound 443 from the internet.
- **API SG:** application port from ALB SG only.
- **Worker SG:** no public inbound.
- **DB SG:** 5432 from API and worker SGs.

**ALB health check**

| Setting | Value |
|---|---|
| Path | `/actuator/health` |
| Interval | 30 s |
| Timeout | 5 s |
| Healthy threshold | 2 |
| Unhealthy threshold | 3 |

**API hostnames (HTTPS via ACM + Route 53 alias → ALB)**

| Env | Hostname |
|---|---|
| DEV | `api-dev.vikranthreddy.com` |
| PROD | `api.vikranthreddy.com` |

Hosted zone for `vikranthreddy.com` already exists in Route 53 (domain purchased there). Terraform manages ACM certificates and the API alias records.

**Web hostnames (manual Route 53 CNAME → Vercel; not Terraform)**

| Env | Hostname | Vercel environment (Hobby) |
|---|---|---|
| DEV | `app-dev.vikranthreddy.com` | Preview (or dedicated DEV project) |
| PROD | `app.vikranthreddy.com` | Production (`main`) |

See [`docs/vercel-deploy.md`](vercel-deploy.md) and [`docs/auth0-setup.md`](auth0-setup.md).

---

## 4. Compute summary

| Service | Image | Role env |
|---|---|---|
| `api` | `dwellio-api` ECR | — |
| `billing` | `dwellio-worker` ECR | `WORKER_ROLE=billing` |
| `invoice` | `dwellio-worker` ECR | `WORKER_ROLE=invoice` |

Terraform sets `lifecycle.ignore_changes` on ECS `desired_count` so sleep/wake does not fight state.

Temporary DB access: SSM port-forward (documented in Architecture); no extra AWS data-plane services.

---

## 5. SQS & EventBridge

| Setting | Billing trigger | Invoice |
|---|---|---|
| Type | Standard | Standard |
| Visibility timeout | 5 min | 10 min |
| Long poll | 20 s | 20 s |
| Retention | 4 days | 7 days |
| Max receives | 3 | 3 |
| DLQ | Yes | Yes |

- Invoice processing transaction timeout: **5 minutes** (application).
- Workers **delete** messages after successful processing (at-least-once + idempotency in app).

**EventBridge / Scheduler:** on the **1st of each month at 23:00 Asia/Kolkata (IST)**, enqueue a message to the Billing Trigger queue (fan-out runs in the billing worker).

---

## 6. S3 documents

- Private buckets; Block Public Access on; encryption on; CORS per environment.
- Abort incomplete multipart uploads after **1 day**.
- Browser finishes upload → API `HeadObject` → mark document status **`UPLOADED`** (app; no `ARCHIVED` in MVP).

---

## 7. Secrets Manager

Terraform creates secret **stubs** (names/ARNs). Sensitive values are set out-of-band (console/CLI) — never committed to git. `dwellio-*/app` secret versions use `lifecycle.ignore_changes` on `secret_string` so applies do not wipe console edits.

| Secret | Purpose | Keys used by ECS |
|---|---|---|
| `{prefix}/db` | App DB connection hints | `username` → `SPRING_DATASOURCE_USERNAME` |
| `rds!cluster-…` (RDS-managed) | Aurora master password | `password` → `SPRING_DATASOURCE_PASSWORD` |
| `{prefix}/app` | App config | `AUTH0_ISSUER_URI`, `AUTH0_AUDIENCE` |

ECS also sets non-secret env: `SPRING_DATASOURCE_URL` (Aurora JDBC), `DWELLIO_DOCUMENTS_BUCKET`, `DWELLIO_S3_REGION`, and clears MinIO-oriented S3 endpoint/keys so the **task role** is used for S3.

Ensure `{prefix}/app` has real Auth0 issuer + audience before the API can finish JWT setup.

---

## 8. Monitoring

- CloudWatch **log groups** for API and worker services.
- Basic CloudWatch **alarms** (e.g. ALB 5xx, ECS CPU, Aurora signals).
- **No SNS** in MVP: alarms are visible in the CloudWatch console only. Email/Slack is a follow-up.

---

## 9. Sleep / wake

| Action | DEV | PROD |
|---|---|---|
| Sleep | Scale ECS desired counts to **0** | Requires explicit confirmation / accepted downtime |
| Wake | Restore capacity table desired counts | Same, after confirmation |
| Deep sleep | Out of scope | Out of scope |

Sleep leaves VPC, Aurora, SQS, S3, etc. in place (cost still accrues for some resources). It is **not** the same as destroy.

Example (DEV sleep — adjust service names to match Terraform):

```bash
aws ecs update-service --cluster dwellio-dev --service api --desired-count 0 --region ap-south-1
aws ecs update-service --cluster dwellio-dev --service billing --desired-count 0 --region ap-south-1
aws ecs update-service --cluster dwellio-dev --service invoice --desired-count 0 --region ap-south-1
```

Example (PROD sleep — **only after explicit confirmation**):

```bash
aws ecs update-service --cluster dwellio-prod --service api --desired-count 0 --region ap-south-1
aws ecs update-service --cluster dwellio-prod --service billing --desired-count 0 --region ap-south-1
aws ecs update-service --cluster dwellio-prod --service invoice --desired-count 0 --region ap-south-1
```

Wake: set desired counts back to the capacity table (DEV API `1`, billing/invoice per table; PROD API `2`, invoice `1`).

---

## 10. Terraform apply / destroy (per environment)

Same commands; different directory and state key.

```bash
# DEV
cd terraform/environments/dev
terraform init
terraform plan
terraform apply    # only with explicit human approval
terraform destroy  # tears down DEV only

# PROD
cd terraform/environments/prod
terraform init
terraform plan
terraform apply    # not automatic in Tasks 42–47; requires separate approval
terraform destroy  # tears down PROD only — never touches DEV
```

**Apply policy (Tasks 42–47):** ship IaC + CI; apply **DEV** only after human approval of the plan; **PROD** remains plan-only until you explicitly approve later.

---

## 11. Remote state bootstrap

- Envs start with **local** state (`terraform/environments/*/backend.tf`) so plan works before the bucket exists.
- After bootstrap: copy `backend.s3.tf.example` → `backend.tf`, then `terraform init -migrate-state`.
- Remote: **S3** + **S3 native lockfile** (no DynamoDB).
- Example key layout: `dwellio/terraform/dev/terraform.tfstate`, `dwellio/terraform/prod/terraform.tfstate`.
- Details: `terraform/README.md`.

---

## 12. GitHub Actions & OIDC

**Repo trust:** `supreme-reality/dwellio` (human-readable). IAM trust uses GitHub’s **ID-qualified** OIDC `sub` prefix from Terraform var `github_oidc_sub_prefix` (e.g. `repo:supreme-reality@73769022/dwellio@1377241890:*`), not classic `repo:org/repo:*`.

**No long-lived AWS access keys in GitHub Secrets.** Workflows assume IAM roles via GitHub → AWS OIDC:

1. IAM OIDC provider for `token.actions.githubusercontent.com`.
2. Role trust `StringLike` on token `sub` = `"${github_oidc_sub_prefix}:*"` (covers `environment:…` and `ref:…`).
3. Job uses `aws-actions/configure-aws-credentials` with `role-to-assume`.
4. STS returns short-lived credentials for that job only.

If assume-role fails with `Not authorized to perform sts:AssumeRoleWithWebIdentity`, check CloudTrail `AssumeRoleWithWebIdentity` → `userName` / `principalId` for the actual `sub`, then update `github_oidc_sub_prefix` in tfvars and re-apply.

| Workflow | Behavior |
|---|---|
| `terraform.yml` | PR: fmt / validate / plan; `main`: apply DEV with Environment approval; PROD apply via protected `workflow_dispatch` |
| `api-deploy.yml` | Build API + worker images → ECR → update ECS (worker image to billing **and** invoice). Push `dev` → DEV ECR/ECS; push `main` → PROD ECR/ECS; `workflow_dispatch` can override. |

**GitHub Variables** (set after first successful Terraform apply that creates roles/ECR):

| Variable | Example |
|---|---|
| `AWS_REGION` | `ap-south-1` |
| `AWS_ROLE_ARN_TERRAFORM_DEV` | Terraform output `gha_terraform_role_arn` |
| `AWS_ROLE_ARN_DEPLOY_DEV` | Terraform output `gha_deploy_role_arn` |
| `ECS_CLUSTER_DEV` | `dwellio-dev` |
| `ECR_API_REPOSITORY_DEV` | output `api_ecr_repository_url` |
| `ECR_WORKER_REPOSITORY_DEV` | output `worker_ecr_repository_url` |
| `AWS_ROLE_ARN_DEPLOY_PROD` | prod output `gha_deploy_role_arn` |
| `ECS_CLUSTER_PROD` | `dwellio-prod` |
| `ECR_API_REPOSITORY_PROD` | prod output `api_ecr_repository_url` |
| `ECR_WORKER_REPOSITORY_PROD` | prod output `worker_ecr_repository_url` |

Create GitHub Environments `development` and `production` with required reviewers for apply jobs.

**Web:** deploy via **Vercel Git integration** — see `docs/vercel-deploy.md`. Do not add a duplicate GitHub deploy workflow unless needed later.

---

## 13. Scope boundary (no new AWS services)

Tickets, bed blocking, tenant `organization_id`, and document status enums are **Aurora / application** concerns. Infrastructure does **not** add services for those features.

---

## 14. Related commands cheat sheet

| Goal | Command / place |
|---|---|
| Format / validate TF | `terraform fmt -check -recursive terraform/` ; `terraform validate` in env dir |
| Plan DEV | `cd terraform/environments/dev && terraform plan` |
| Destroy one env | `cd terraform/environments/{dev\|prod} && terraform destroy` |
| Sleep DEV | ECS `update-service` desired-count `0` (§9) |
| API health (local) | `curl -s http://localhost:8080/actuator/health` |
| API health (deployed) | `curl -s https://api-dev.vikranthreddy.com/actuator/health` |
