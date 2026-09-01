-- Payment integrity + move-in Razorpay pending deposit intent
ALTER TABLE payment
    ADD COLUMN intended_deposit_amount NUMERIC(12, 2);

ALTER TABLE payment
    ADD CONSTRAINT chk_payment_intended_deposit_nonneg
        CHECK (intended_deposit_amount IS NULL OR intended_deposit_amount >= 0);

CREATE UNIQUE INDEX uq_payment_external_reference
    ON payment (external_reference)
    WHERE external_reference IS NOT NULL;
