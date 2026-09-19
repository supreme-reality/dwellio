# Billing worker

Spring Boot worker that **fans out** monthly `billing_run` / `billing_run_item` rows for active stays.

- Core logic: `com.dwellio.api.billing.BillingFanOutService`
- Does **not** create invoices (see `apps/invoice-worker`)
- Local/test trigger: `POST /api/v1/internal/billing/fan-out` (API profile `local`/`test` only)
- Production: EventBridge → Billing Trigger SQS → this worker (SQS listener TBD with Terraform)

## DLQ

After max receives (Infra: 3), messages land on the Billing Trigger DLQ. Investigate failed fan-out runs via `billing_run` / CloudWatch; fix data; replay from DLQ.
