-- V5: payments, invoices, deposit ledger, settlement (DB Schema v5.0 §§8–11 / v1.5 §§8–11)

CREATE TABLE deposit_ledger (
    id           UUID PRIMARY KEY,
    tenancy_id   UUID           NOT NULL,
    type         VARCHAR(20)    NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    reference    VARCHAR(160),
    notes        TEXT,
    created_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_deposit_ledger_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT chk_deposit_ledger_type CHECK (type IN ('RECEIPT', 'DEDUCTION', 'REFUND')),
    CONSTRAINT chk_deposit_ledger_amount CHECK (amount >= 0)
);

CREATE INDEX idx_deposit_ledger_tenancy_id ON deposit_ledger (tenancy_id);

CREATE TABLE invoice (
    id              UUID PRIMARY KEY,
    tenancy_id      UUID           NOT NULL,
    invoice_type    VARCHAR(20)    NOT NULL,
    billing_period  DATE,
    billing_date    DATE           NOT NULL,
    due_date        DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    currency        CHAR(3)        NOT NULL,
    subtotal        NUMERIC(12, 2) NOT NULL,
    total           NUMERIC(12, 2) NOT NULL,
    finalized_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_invoice_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('MONTHLY', 'CHECKOUT', 'UPFRONT')),
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT', 'FINALIZED', 'VOID')),
    CONSTRAINT chk_invoice_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_invoice_total CHECK (total >= 0)
);

CREATE INDEX idx_invoice_tenancy_id ON invoice (tenancy_id);
CREATE INDEX idx_invoice_status ON invoice (status);
CREATE INDEX idx_invoice_billing_period ON invoice (billing_period);

CREATE TABLE invoice_line_item (
    id            UUID PRIMARY KEY,
    invoice_id    UUID           NOT NULL,
    line_type     VARCHAR(30)    NOT NULL,
    description   VARCHAR(300)   NOT NULL,
    quantity      NUMERIC(12, 3) NOT NULL,
    unit_amount   NUMERIC(12, 2) NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    reference_id  UUID,
    created_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_invoice_line_item_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT chk_invoice_line_type CHECK (
        line_type IN ('RENT', 'SERVICE', 'DAMAGE', 'CLEANING', 'OTHER')
    ),
    CONSTRAINT chk_invoice_line_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_invoice_line_unit_amount CHECK (unit_amount >= 0),
    CONSTRAINT chk_invoice_line_amount CHECK (amount >= 0)
);

CREATE INDEX idx_invoice_line_item_invoice_id ON invoice_line_item (invoice_id);

CREATE TABLE payment (
    id                       UUID PRIMARY KEY,
    organization_id          UUID           NOT NULL,
    tenancy_id               UUID,
    payment_method           VARCHAR(20)    NOT NULL,
    amount                   NUMERIC(12, 2) NOT NULL,
    currency                 CHAR(3)        NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    external_reference       VARCHAR(200),
    bank_transfer_reference  VARCHAR(200),
    idempotency_key          VARCHAR(200),
    confirmed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_payment_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_payment_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT chk_payment_method CHECK (
        payment_method IN ('RAZORPAY', 'CASH', 'BANK_TRANSFER')
    ),
    CONSTRAINT chk_payment_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_bank_ref CHECK (
        payment_method <> 'BANK_TRANSFER'
        OR (bank_transfer_reference IS NOT NULL AND length(trim(bank_transfer_reference)) > 0)
    )
);

CREATE UNIQUE INDEX uq_payment_idempotency_key
    ON payment (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_payment_organization_id ON payment (organization_id);
CREATE INDEX idx_payment_tenancy_id ON payment (tenancy_id);
CREATE INDEX idx_payment_status ON payment (status);

CREATE TABLE payment_invoice (
    id          UUID PRIMARY KEY,
    payment_id  UUID           NOT NULL,
    invoice_id  UUID           NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_payment_invoice_payment
        FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_payment_invoice_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT uq_payment_invoice UNIQUE (payment_id, invoice_id),
    CONSTRAINT chk_payment_invoice_amount CHECK (amount >= 0)
);

CREATE INDEX idx_payment_invoice_payment_id ON payment_invoice (payment_id);
CREATE INDEX idx_payment_invoice_invoice_id ON payment_invoice (invoice_id);

CREATE TABLE settlement_snapshot (
    id                       UUID PRIMARY KEY,
    tenancy_id               UUID           NOT NULL,
    occupancy_id             UUID           NOT NULL,
    checkout_date            DATE           NOT NULL,
    outstanding_receivable   NUMERIC(12, 2) NOT NULL,
    new_checkout_charges     NUMERIC(12, 2) NOT NULL,
    deposit_balance_before   NUMERIC(12, 2) NOT NULL,
    deposit_deduction        NUMERIC(12, 2) NOT NULL,
    deposit_refund_due       NUMERIC(12, 2) NOT NULL,
    total_receivable         NUMERIC(12, 2) NOT NULL,
    net_receivable           NUMERIC(12, 2) NOT NULL,
    refund_due               NUMERIC(12, 2) NOT NULL,
    currency                 CHAR(3)        NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    confirmed_at             TIMESTAMPTZ    NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_settlement_snapshot_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT fk_settlement_snapshot_occupancy
        FOREIGN KEY (occupancy_id) REFERENCES occupancy (id),
    CONSTRAINT chk_settlement_snapshot_status CHECK (status = 'CONFIRMED')
);

CREATE INDEX idx_settlement_snapshot_tenancy_id ON settlement_snapshot (tenancy_id);
CREATE INDEX idx_settlement_snapshot_occupancy_id ON settlement_snapshot (occupancy_id);
