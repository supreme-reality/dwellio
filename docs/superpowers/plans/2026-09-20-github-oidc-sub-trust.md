# GitHub OIDC ID-based `sub` trust — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make GHA OIDC assume-role succeed by aligning IAM trust `sub` with GitHub’s ID-qualified subject prefix.

**Architecture:** Add `github_oidc_sub_prefix` through env → IAM module; build `StringLike` as `"${prefix}:*"`. Keep `github_repository` for human-readable docs only.

**Tech Stack:** Terraform AWS provider ≥5, IAM OIDC (`token.actions.githubusercontent.com`), GitHub Actions `api-deploy.yml` (unchanged).

**Spec:** `docs/superpowers/specs/2026-09-20-github-oidc-sub-trust-design.md`

## Global Constraints

- Prefix value (verbatim): `repo:supreme-reality@73769022/dwellio@1377241890`
- Module appends `:*`; do not put `:*` in tfvars
- Same prefix for DEV and PROD
- Do not recreate the OIDC provider unless plan requires it
- Do not change GitHub Actions workflows or GitHub Variables for this fix

## File map

| File | Change |
|---|---|
| `terraform/modules/iam/variables.tf` | Add `github_oidc_sub_prefix` |
| `terraform/modules/iam/main.tf` | Use prefix for `oidc_sub_repo` |
| `terraform/modules/iam/outputs.tf` | Output effective pattern (already wired; confirm) |
| `terraform/environments/dev/variables.tf` | Pass-through variable |
| `terraform/environments/dev/main.tf` | Pass into `module.iam` |
| `terraform/environments/dev/terraform.tfvars` | Set prefix |
| `terraform/environments/prod/variables.tf` | Pass-through variable |
| `terraform/environments/prod/main.tf` | Pass into `module.iam` |
| `terraform/environments/prod/terraform.tfvars` | Set prefix |
| `docs/infrastructure-runbook.md` | Document ID-based `sub` + CloudTrail recovery |

---

### Task 1: IAM module — configurable OIDC `sub` prefix

**Files:**
- Modify: `terraform/modules/iam/variables.tf`
- Modify: `terraform/modules/iam/main.tf` (locals ~16–19 and assume policy condition)
- Modify: `terraform/modules/iam/outputs.tf` (confirm `oidc_sub_condition` uses new local)

**Interfaces:**
- Consumes: `var.github_oidc_sub_prefix` (string, no trailing `:*`)
- Produces: `local.oidc_sub_repo = "${var.github_oidc_sub_prefix}:*"` used by `data.aws_iam_policy_document.gha_assume`

- [ ] **Step 1: Add variable**

```hcl
variable "github_oidc_sub_prefix" {
  type        = string
  description = "GitHub OIDC sub prefix before context (e.g. repo:org@id/repo@id). Module appends :*"
}
```

- [ ] **Step 2: Change local** — replace `repo:${var.github_repository}:*` with `"${var.github_oidc_sub_prefix}:*"`. Leave `github_repository` variable in place (unused by trust) or mark description as “docs only”.

- [ ] **Step 3: Confirm output** `oidc_sub_condition` still returns `local.oidc_sub_repo`.

- [ ] **Step 4: Commit** (message: `fix(iam): use configurable GitHub OIDC sub prefix for role trust`)

---

### Task 2: Wire DEV + PROD env vars and tfvars

**Files:**
- Modify: `terraform/environments/dev/variables.tf`
- Modify: `terraform/environments/dev/main.tf` (`module "iam"` block)
- Modify: `terraform/environments/dev/terraform.tfvars`
- Modify: `terraform/environments/prod/variables.tf`
- Modify: `terraform/environments/prod/main.tf`
- Modify: `terraform/environments/prod/terraform.tfvars`

**Interfaces:**
- Consumes: Task 1 module variable
- Produces: Both envs pass `github_oidc_sub_prefix = "repo:supreme-reality@73769022/dwellio@1377241890"`

- [ ] **Step 1: Add env-level variable** (dev and prod), same type/description as module.

- [ ] **Step 2: Pass into module.iam** in both `main.tf` files:

```hcl
github_oidc_sub_prefix = var.github_oidc_sub_prefix
```

- [ ] **Step 3: Set tfvars** (dev and prod):

```hcl
github_oidc_sub_prefix = "repo:supreme-reality@73769022/dwellio@1377241890"
```

- [ ] **Step 4: Commit** (message: `chore(terraform): set GitHub OIDC sub prefix for dwellio roles`)

---

### Task 3: Runbook + DEV apply + verify

**Files:**
- Modify: `docs/infrastructure-runbook.md` (§12 GitHub Actions & OIDC)

**Interfaces:**
- Consumes: Applied trust from Tasks 1–2
- Produces: Working GHA OIDC for API deploy

- [ ] **Step 1: Document** that trust uses ID-based `sub`; how to refresh prefix from CloudTrail `userName` if AccessDenied returns.

- [ ] **Step 2: Plan DEV**

```bash
cd terraform/environments/dev
terraform plan -out=dev-oidc.tfplan
```

Expect: update assume-role policy on `dwellio-dev-gha-deploy` and `dwellio-dev-gha-terraform` only (or primarily).

- [ ] **Step 3: Apply DEV** (after operator approval): `terraform apply dev-oidc.tfplan`

- [ ] **Step 4: Verify IAM**

```bash
aws iam get-role --role-name dwellio-dev-gha-deploy \
  --query 'Role.AssumeRolePolicyDocument.Statement[0].Condition' --output json
```

Must show `StringLike` → `repo:supreme-reality@73769022/dwellio@1377241890:*`.

- [ ] **Step 5: Re-run** GitHub Action **API deploy** (or Re-run failed jobs). Confirm OIDC step green.

- [ ] **Step 6: Commit runbook** if not already committed with earlier tasks.

---

## Done when

- [ ] DEV role trust matches ID-based prefix
- [ ] API deploy OIDC step succeeds
- [ ] Spec + plan + code/docs committed (commits only if user requested)
