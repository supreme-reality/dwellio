# Dwellio Infra + Terraform/CI Design (Tasks 42–47)

**Date:** 2026-09-19  
**Status:** Approved for planning  
**Scope:** MVP plan Tasks 42–47 (runbook, Terraform modules/envs, GitHub Actions)  
**Canonical specs:** `projectDocs/v5/StayFlow_Infrastructure_v5.0.md`, `StayFlow_Terraform_v5.0.md`, Architecture v5.0 (EventBridge billing)

---

## 1. Goal

Deliver AWS infrastructure-as-code and CI for Dwellio on a single AWS account in `ap-south-1`, aligned to Infra/Terraform v5.0 topology (no new AWS services for tickets/bed-block/etc.), with DEV apply gated on human approval and PROD plan-only until explicitly approved later.

---

## 2. Locked decisions

| Topic | Decision |
|---|---|
| Region | `ap-south-1` (Mumbai) |
| Accounts | One AWS account; DEV + PROD as separate Terraform environments |
| Approach | Spec-faithful modules (`vpc`, `alb`, `ecs`, `aurora`, `s3`, `sqs`, `iam`, `monitoring`) + `environments/{dev,prod}` |
| API DNS | DEV `api-dev.vikranthreddy.com`; PROD `api.vikranthreddy.com` |
| Domain | `vikranthreddy.com` already in Route 53 (purchased there); Terraform manages ACM + API alias records |
| EventBridge | Included in Terraform: 1st of month 23:00 IST → Billing Trigger SQS |
| OIDC repo trust | `supreme-reality/dwellio` only |
| Remote state | S3 backend + S3 native lockfile (no DynamoDB); one-time bucket bootstrap documented |
| Apply policy | Apply DEV with human approval; PROD plan-only in this phase |
| Monitoring | CloudWatch log groups + basic alarms; **no SNS email/Slack yet** (alarms visible in console) |
| Workers | One worker ECR image; two ECS services (`WORKER_ROLE=billing\|invoice`) |
| Sleep/wake | Scale ECS `desired_count`; Terraform `ignore_changes` on `desired_count`; deep sleep out of scope |
| Destroy | `terraform destroy` per environment directory only |
| Web deploy | Vercel Git integration; document only (no duplicate GH deploy) |

---

## 3. Repository layout

```
terraform/
  modules/
    vpc/
    alb/          # + ACM cert + Route 53 alias for API hostname
    ecs/          # + ECR repos; api + billing + invoice services
    aurora/
    s3/
    sqs/          # queues, DLQs, EventBridge schedule → billing queue
    iam/          # GitHub OIDC provider + deploy/plan roles
    monitoring/   # log groups + CloudWatch alarms (no SNS)
  environments/
    dev/
    prod/
docs/infrastructure-runbook.md
.github/workflows/terraform.yml
.github/workflows/api-deploy.yml
apps/api/Dockerfile
apps/worker/Dockerfile
```

Extra resources required by Architecture/CI but not named as top-level modules in Terraform v5 are folded into the closest module (ECR → `ecs`, EventBridge → `sqs`, ACM/DNS → `alb`). No Terragrunt.

---

## 4. Network, data, queues, objects, secrets

### 4.1 VPC

- DEV CIDR `10.10.0.0/16`; PROD `10.20.0.0/16`
- 2 AZs; public (ALB), private app (ECS), private DB (Aurora)
- 1 NAT Gateway initially; no public IPs on tasks

### 4.2 Security groups

- ALB: 443 from internet
- API: application port from ALB only
- Workers: no public inbound
- DB: 5432 from API and worker SGs

### 4.3 ALB + TLS + DNS

- HTTPS listener with ACM certificate for the env API hostname
- Target group health check: `/actuator/health` (interval 30s, timeout 5s, healthy threshold 2, unhealthy 3)
- Route 53 alias record → ALB

### 4.4 Aurora

- Serverless v2: DEV 0–2 ACU; PROD 0.5–4 ACU
- Private subnets only

### 4.5 SQS + EventBridge

| Queue | Visibility | Retention | Max receives | DLQ |
|---|---|---|---|---|
| Billing trigger | 5 min | 4 days | 3 | Yes |
| Invoice | 10 min | 7 days | 3 | Yes |

- Long poll 20s
- EventBridge Scheduler: cron for 1st of month 23:00 Asia/Kolkata → Billing Trigger queue

### 4.6 S3 documents

- Private bucket, Block Public Access, encryption, env-specific CORS
- Abort incomplete multipart uploads after 1 day
- Application marks documents `UPLOADED` after HeadObject (app logic; bucket policy/CORS in Terraform)

### 4.7 Secrets Manager

- Stub secrets for DB and app config; secret *values* set out-of-band (console/CLI), never committed

---

## 5. Compute, IAM/OIDC, sleep

### 5.1 ECR + ECS

- ECR repositories for API and worker images
- Three Fargate services: `api`, `billing`, `invoice`
- Billing and invoice share the worker image; task env `WORKER_ROLE=billing` or `invoice`
- Task size: DEV 0.25 vCPU / 512 MB; PROD 0.5 vCPU / 1 GB

Capacity (min / desired / max):

| Service | DEV | PROD |
|---|---|---|
| API | 0 / 1 / 2 | 2 / 2 / 4 |
| Billing | 0 / 0 / 1 | 0 / 0 / 1 |
| Invoice | 0 / 0 / 1 | 1 / 1 / 5 |

ECS services use `lifecycle { ignore_changes = [desired_count] }` for sleep/wake.

### 5.2 Sleep / wake / destroy

- DEV sleep: scale desired counts to 0; wake restores capacity table
- PROD sleep: requires explicit confirmation (runbook + GitHub environment protection)
- Destroy: `cd terraform/environments/{dev|prod} && terraform destroy` affects that env only
- Sleep ≠ destroy (VPC/Aurora/SQS remain on sleep)

### 5.3 GitHub OIDC (no long-lived AWS keys)

**Problem:** Actions need AWS API access without storing `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in GitHub Secrets.

**Mechanism:**

1. Terraform creates an IAM OIDC identity provider for `token.actions.githubusercontent.com`.
2. IAM roles trust that provider with a condition on the token **subject** (`sub`), e.g. `repo:supreme-reality/dwellio:*` (optionally tightened to `ref:refs/heads/main` for apply).
3. Workflow uses `aws-actions/configure-aws-credentials` with `role-to-assume`.
4. GitHub mints a short-lived OIDC JWT for the job; AWS STS `AssumeRoleWithWebIdentity` returns temporary credentials.
5. Job uses those credentials; they expire after the session.

**“Context” here** means the OIDC JWT claims AWS evaluates, especially:

| Claim | Meaning for us |
|---|---|
| `sub` | Who is calling — e.g. `repo:supreme-reality/dwellio:ref:refs/heads/main` |
| `aud` | Audience (typically `sts.amazonaws.com`) |
| `repository` | `supreme-reality/dwellio` |

Trusted repo: **`supreme-reality/dwellio`** only (`origin` remote). Roles cover Terraform plan/apply and ECR push + ECS service update.

GitHub may still hold **non-AWS** secrets (e.g. nothing required for AWS auth itself); AWS access is OIDC-only.

---

## 6. Monitoring

- CloudWatch log groups for API and worker services
- Basic CloudWatch Alarms (ALB 5xx, ECS CPU, Aurora-related signals as practical)
- **No SNS** in this phase — alarms appear in the CloudWatch console only; email/Slack is a follow-up

---

## 7. CI / CD workflows

### 7.1 `terraform.yml`

- PR: `terraform fmt -check`, `validate`, `plan` (DEV; PROD plan via dispatch or path rules)
- `main`: apply DEV only with GitHub Environment approval
- PROD apply: `workflow_dispatch` + protection; not automatic in Tasks 42–47

### 7.2 `api-deploy.yml`

- Build API + worker Docker images → push ECR → update ECS (worker image rolled to billing and invoice)
- Auth via OIDC

### 7.3 Web

- Document Vercel Git integration; no second deploy pipeline unless later needed

---

## 8. Task mapping

| Task | Deliverable |
|---|---|
| 42 | `docs/infrastructure-runbook.md` 1:1 with Infra v5 + decisions above |
| 43 | Module skeleton + env roots + backend notes; `validate` / `fmt` |
| 44 | DEV VPC, ALB/ACM/DNS, Aurora, SQS+DLQs+EventBridge, S3, Secrets stubs, SGs |
| 45 | ECR, three ECS services, GitHub OIDC roles |
| 46 | Workflows + Dockerfiles; Vercel documented |
| 47 | PROD env parity (CIDR/capacity/hostname); plan-only apply |

---

## 9. Out of scope (this design)

- SNS / PagerDuty / Slack alarm routing
- Terragrunt
- Separate AWS accounts
- Deep sleep
- Automatic PROD apply
- New AWS services beyond v5 topology
- Phase 8 Vercel/app subdomain wiring (except API DNS above)
- Filling Secrets Manager values / Auth0 production cutover details beyond stubs

---

## 10. Verification

- Runbook checklist maps to Infra v5.0
- `terraform init && validate` and `fmt -check` for envs
- Plans reviewable; DEV apply only with explicit human approval
- Plan shows three ECS services, one worker image usage pattern, OIDC trust limited to `supreme-reality/dwellio`
- Workflow files present; `actionlint` if available
- PROD `terraform plan` matches capacity table; no unintended drift from DEV module contract
