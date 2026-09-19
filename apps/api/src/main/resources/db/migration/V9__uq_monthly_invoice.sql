-- Prevent duplicate MONTHLY invoices for the same stay + period (money idempotency).
-- VOID rows are excluded so a voided invoice can be replaced.

CREATE UNIQUE INDEX uq_invoice_monthly_tenancy_period
    ON invoice (tenancy_id, billing_period)
    WHERE invoice_type = 'MONTHLY' AND status <> 'VOID';
