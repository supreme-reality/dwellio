# StayFlow — AWS Infrastructure Specification v5.0

**Alignment-only revision**  
**Aligned with:** Architecture v5.0 · Project Context v5.0  

Supersedes Infrastructure v1.3 **without material AWS changes**. Domain gaps resolved in Context/FR/API/DB v5.0 do not introduce new infrastructure components.

---

## 1. Version status

| Item | Status |
|---|---|
| VPC / ALB / ECS / Aurora / SQS / S3 / Secrets / OIDC / CloudWatch / SSM | Unchanged from v1.3 |
| Billing schedule | EventBridge 23:00 IST on the 1st |
| Document upload verify | Backend HeadObject before `UPLOADED` |
| New services for tickets / bed block / tenant org scope | **None** — application/DB only |

---

## 2. Capacity baseline (unchanged)

| Component | DEV | PROD |
|---|---|---|
| VPC | 10.10.0.0/16 | 10.20.0.0/16 |
| AZs | 2 | 2 |
| ALB | 1 | 1 |
| API ECS | 0/1/2 | 2/2/4 |
| Billing Worker | 0/0/1 | 0/0/1 |
| Invoice Worker | 0/0/1 | 1/1/5 |
| Aurora ACU | 0–2 | 0.5–4 |
| NAT | 1 initially | 1 initially; 2 for strict AZ HA |
| SQS | 2 queues + 2 DLQs | 2 queues + 2 DLQs |

Task sizing: DEV 0.25 vCPU / 512 MB; PROD 0.5 vCPU / 1 GB.

---

## 3. Network & security (unchanged)

- Public subnets: ALB. Private app: ECS. Private DB: Aurora. No public IPs on tasks.  
- ALB SG: 443 from internet. API SG: app port from ALB. Worker SG: no public inbound. DB SG: 5432 from API/worker SGs.  
- Health: `/actuator/health` (30s interval, 5s timeout, healthy 2, unhealthy 3).

---

## 4. SQS (unchanged)

| Setting | Billing Trigger | Invoice |
|---|---|---|
| Type | Standard | Standard |
| Visibility | 5 min | 10 min |
| Long poll | 20 s | 20 s |
| Retention | 4 days | 7 days |
| Max receives | 3 | 3 |
| DLQ | Yes | Yes |

Invoice processing transaction timeout 5 minutes. Workers delete successful messages.

---

## 5. S3 documents (unchanged ops; status vocabulary from DB v5.0)

- Private buckets, Block Public Access, encryption, env CORS.  
- Abort incomplete multipart after 1 day.  
- Browser completes ? backend HeadObject ? mark document **`UPLOADED`**.

---

## 6. Sleep / wake (unchanged)

- DEV sleep scales ECS to zero; wake restores capacity.  
- PROD sleep requires confirmation/downtime.  
- Terraform ignores ECS `desired_count` runtime changes.  
- Deep sleep out of scope.

---

## 7. Scope boundary

Tickets, bed blocking, tenant `organization_id`, and document status enums are **Aurora application schema** concerns. No new AWS service is introduced in v5.0.
