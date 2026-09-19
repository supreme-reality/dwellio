# StayFlow — Terraform & IaC Specification v5.0

**Alignment-only revision**  
**Aligned with:** Infrastructure v5.0 · Architecture v5.0  

Supersedes Terraform v1.2 **without material IaC topology changes**.

---

## 1. Version status

Domain model updates in the v5.0 product pack (tickets, bed block, tenant org scope, document statuses) are applied via application migrations against Aurora. **No new Terraform modules** are required for those features.

---

## 2. Repository structure (unchanged)

```
terraform/
  modules/   (vpc, ecs, alb, aurora, s3, sqs, iam, monitoring)
  environments/ (dev, prod)
```

State: S3-backed remote state with separate DEV/PROD keys; bootstrap backend separately.

---

## 3. Capacity variables (unchanged)

| Variable | DEV | PROD |
|---|---|---|
| api_min / desired / max | 0 / 1 / 2 | 2 / 2 / 4 |
| billing_worker min/desired/max | 0/0/1 | 0/0/1 |
| invoice_worker min/desired/max | 0/0/1 | 1/1/5 |
| aurora_min_acu / max_acu | 0 / 2 | 0.5 / 4 |

---

## 4. Modules (unchanged)

VPC, ALB, ECS, Aurora, SQS, S3, IAM (GitHub OIDC), Monitoring.

ECS services must `ignore_changes` on `desired_count` for sleep/wake.

---

## 5. GitHub Actions (unchanged)

- Infra: PR ? fmt/validate/plan ? approval ? apply  
- App: build image ? ECR ? ECS update  
- Auth: OIDC short-lived roles; no long-lived AWS keys in GitHub Secrets  

---

## 6. SQS / S3 notes (unchanged)

- Invoice visibility 10 min; Spring txn timeout 5 min; explicit delete; at-least-once + idempotency.  
- Presigned upload; frontend high-level uploader; complete + HeadObject ? `UPLOADED`.

---

## 7. Sleep / wake (unchanged)

Deep sleep excluded. DEV sleep ? zero desired count; PROD requires environment approval.

---

## 8. Version change — v1.2 ? v5.0

- Document pack alignment to v5.0.  
- Document status vocabulary reference updated to `UPLOADED`.  
- No module, capacity, or topology changes.
