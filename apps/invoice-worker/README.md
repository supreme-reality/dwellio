# Invoice worker

Polls `billing_run_item` rows in `PENDING` and creates MONTHLY invoices via
`MonthlyInvoiceCreationService` (per-item transactions, FAILED isolation).

SQS consumer wiring replaces the poller when Terraform/ECS queues are ready.
Message shape: `InvoiceJob(billingRunItemId, tenancyId, billingPeriod)`.

Config:
- `dwellio.invoice-worker.poll-enabled` (default true)
- `dwellio.invoice-worker.poll-delay-ms` (default 5000)
