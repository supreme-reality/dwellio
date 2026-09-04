# Invoice worker

Consumes Invoice SQS (or polls PENDING `billing_run_item` rows) and creates/finalizes MONTHLY invoices.

- Core logic: `com.dwellio.api.billing.MonthlyInvoiceCreationService`
- Idempotent per tenancy + billing period
- Delete SQS message only after success; txn timeout ≤ 5 min (Infra)
- DLQ after max receives (3) — document replay in ops runbook

Local/test: `POST /api/v1/internal/billing/process-pending` on API with profile `local`/`test`.
