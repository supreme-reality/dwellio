# GitHub OIDC ID-based `sub` trust — Design

**Date:** 2026-09-20  
**Status:** Approved approach A (align IAM trust to GitHub’s actual `sub`)  
**Scope:** Terraform IAM module + DEV/PROD tfvars + runbook note

## 1. Problem

API deploy (`api-deploy.yml`) fails at **Configure AWS credentials (OIDC)** with:

`Not authorized to perform sts:AssumeRoleWithWebIdentity`

CloudTrail shows the token `sub` GitHub actually issues:

```text
repo:supreme-reality@73769022/dwellio@1377241890:environment:development
```

IAM trust on `dwellio-dev-gha-deploy` / `dwellio-dev-gha-terraform` currently allows only:

```text
repo:supreme-reality/dwellio:*
```

Those strings do not match, so STS denies assume-role. GitHub Variables and the OIDC provider are fine; no long-lived AWS keys are required.

## 2. Why the mismatch existed

Terraform was written for GitHub’s **classic** subject form `repo:ORG/REPO:…`.

The `supreme-reality` org (or this repo) uses GitHub’s **ID-qualified** OIDC subject customization, embedding owner/repo numeric IDs in `sub` so renames do not invalidate trust. That format was not reflected in `terraform/modules/iam` when roles were created.

## 3. Decision

**Approach A:** Keep GitHub claim settings as-is. Update AWS IAM trust `StringLike` on `token.actions.githubusercontent.com:sub` to the ID-based prefix observed in CloudTrail.

Rejected for this change:

- **B** — Revert GitHub OIDC customization to classic `org/repo` (org-level; weaker rename safety).
- **C** — Allow both classic and ID-based patterns (broader than needed).

## 4. Design

### 4.1 Trust condition

Both GHA roles (terraform + deploy) share one assume-role policy document. Change the `sub` condition value from:

```text
repo:${var.github_repository}:*
```

to:

```text
${var.github_oidc_sub_prefix}:*
```

Where `github_oidc_sub_prefix` is set in env tfvars to:

```text
repo:supreme-reality@73769022/dwellio@1377241890
```

(No trailing `:*` in the variable — the module appends `:*`.)

### 4.2 Variables

| Variable | Purpose |
|---|---|
| `github_repository` | Keep for docs / human-readable “trusted repo” (e.g. runbook). Stop using it to build the IAM `sub` condition. |
| `github_oidc_sub_prefix` | **Required** for trust. Exact prefix from GitHub OIDC `sub` before the final `:context` segment. |

Same prefix for DEV and PROD: one GitHub repo, one subject namespace; contexts differ (`environment:development` vs `environment:production` / refs), covered by `:*`.

### 4.3 Outputs

Update `oidc_sub_condition` output to emit the effective pattern (`${prefix}:*`) so operators can verify after apply.

### 4.4 Docs

Short note in `docs/infrastructure-runbook.md` § GitHub Actions & OIDC:

- Trust uses ID-based `sub`, not `org/repo`.
- If assume fails again after a repo transfer/fork, read CloudTrail `userName` / `principalId` and update `github_oidc_sub_prefix`.

### 4.5 Apply / verify

1. `terraform plan` in `terraform/environments/dev` — expect in-place update of both role assume policies only (no recreate of OIDC provider required).
2. Apply DEV.
3. Re-run **API deploy**; OIDC step succeeds; CloudTrail `AssumeRoleWithWebIdentity` without `AccessDenied`.
4. PROD tfvars + apply when PROD GHA roles are in use (same prefix).

## 5. Out of scope

- Changing GitHub org OIDC subject templates.
- GitHub Actions variable values (already correct).
- Environment name casing (`production` vs `Production`) — separate issue.
- Local Docker → ECR path.

## 6. Success criteria

- DEV deploy role trust `sub` is `repo:supreme-reality@73769022/dwellio@1377241890:*`.
- `api-deploy.yml` passes **Configure AWS credentials (OIDC)** on `supreme-reality/dwellio` with environment `development`.
