# Worker

Single Spring Boot worker image for background jobs. Domain logic lives in `apps/api`
(`com.dwellio.api`). Select which consumers run with `WORKER_ROLE`:

| Role | Responsibility |
|---|---|
| `billing` | Monthly billing fan-out (`BillingFanOutService`). SQS listener TBD with Terraform/ECS. |
| `invoice` | PENDING `billing_run_item` → MONTHLY invoices (poller today; Invoice SQS later). |

Deploy the **same image** as separate ECS services with different `WORKER_ROLE` values so
billing and invoice can scale independently.

## Local run

```bash
# Invoice poller
WORKER_ROLE=invoice ./gradlew :apps:worker:bootRun

# Billing (no local poller yet; boots domain context for future SQS consumer)
WORKER_ROLE=billing ./gradlew :apps:worker:bootRun
```

Config:

- `WORKER_ROLE` / `dwellio.worker.role` — `billing` or `invoice`
- `dwellio.invoice-worker.poll-enabled` (default true; only when role is `invoice`)
- `dwellio.invoice-worker.poll-delay-ms` (default 5000)

Local/test fan-out trigger remains on the API: `POST /api/v1/internal/billing/fan-out`
(profiles `local`/`test` only).
