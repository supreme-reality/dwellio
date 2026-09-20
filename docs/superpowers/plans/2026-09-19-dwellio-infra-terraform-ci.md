# Dwellio Infra + Terraform/CI Implementation Plan (Tasks 42–47)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline; **no parallel agents**). Steps use checkbox (`- [ ]`) syntax for tracking. Execute **one task at a time**; stop after Verify passes and wait for partner before Task N+1.

**Goal:** Ship infrastructure runbook, Terraform modules/envs, and GitHub Actions for Dwellio per approved design.

**Architecture:** Single AWS account, `ap-south-1`, modular Terraform (`vpc|alb|ecs|aurora|s3|sqs|iam|monitoring`) + `environments/{dev,prod}`; EventBridge/ACM/ECR folded into existing modules; GitHub OIDC for CI (no long-lived AWS keys).

**Tech Stack:** Terraform ≥ 1.10 (S3 lockfile), AWS provider, GitHub Actions, Docker multi-stage for Java 21.

**Design:** `docs/superpowers/specs/2026-09-19-dwellio-infra-terraform-ci-design.md`

## Global Constraints

- Brand: **Dwellio**; specs under `projectDocs/v5/`
- Region: `ap-south-1`
- No new AWS services beyond v5 topology
- DEV apply only with human approval; PROD plan-only this phase
- OIDC trust: `supreme-reality/dwellio` only
- API hosts: `api-dev.vikranthreddy.com` / `api.vikranthreddy.com`
- Sequential tasks only; commit only when user requests

---

### Task 42: Infrastructure runbook

**Files:**
- Create: `docs/infrastructure-runbook.md`

**Interfaces:**
- Produces: Operator doc mapping 1:1 to Infra v5.0 + design decisions (DNS, OIDC, state, EventBridge, sleep/destroy, monitoring)

- [ ] **Step 1:** Write runbook covering capacity, network/SGs, health, SQS, EventBridge, S3, sleep/wake, destroy, state bootstrap, DNS/TLS, OIDC overview, monitoring (console-only alarms), CI apply policy, Vercel note.
- [ ] **Step 2:** Verify — checklist section maps every Infra v5.0 §2–7 item; include Dwellio-specific decisions from design.
- [ ] **Step 3:** Stop for partner review before Task 43.

**Verify:** Manual: every row in Infra v5 capacity + SG + SQS + S3 + sleep + scope boundary appears in runbook.

---

### Task 43: Terraform modules skeleton

**Files:**
- Create: `terraform/modules/{vpc,alb,ecs,aurora,s3,sqs,iam,monitoring}/` with `main.tf`, `variables.tf`, `outputs.tf` stubs
- Create: `terraform/environments/dev/` and `prod/` with provider, backend stub, root module wiring placeholders
- Create: `terraform/README.md` (state bootstrap)

**Verify:**
```bash
cd terraform/environments/dev && terraform init -backend=false && terraform validate
terraform fmt -check -recursive terraform/
```

---

### Task 44: Terraform DEV core (network + data + queues + buckets)

Implement DEV VPC, ALB/ACM/DNS, Aurora, SQS+DLQs+EventBridge, S3, Secrets stubs, SGs per design §4.

**Verify:** `terraform plan` reviewable (no apply without human OK).

---

### Task 45: ECS + ECR + GitHub OIDC

Three ECS services, ECR, OIDC roles; `ignore_changes` on `desired_count`.

**Verify:** Plan shows api + billing + invoice; OIDC trust limited to `supreme-reality/dwellio`.

---

### Task 46: GitHub Actions + Dockerfiles

`terraform.yml`, `api-deploy.yml`, API/worker Dockerfiles; document Vercel.

**Verify:** Workflow files present; `actionlint` if available.

---

### Task 47: PROD env parity

PROD CIDR/capacity/hostname; plan-only.

**Verify:** `terraform plan` for prod matches capacity table.

---
