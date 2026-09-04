-- V8: billing run fan-out idempotency (Architecture §9)

CREATE TABLE billing_run (
    id              UUID PRIMARY KEY,
    billing_period  DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_billing_run_period UNIQUE (billing_period),
    CONSTRAINT chk_billing_run_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE billing_run_item (
    id               UUID PRIMARY KEY,
    billing_run_id   UUID           NOT NULL,
    tenancy_id       UUID           NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    invoice_id       UUID,
    error_message    TEXT,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_billing_run_item_run
        FOREIGN KEY (billing_run_id) REFERENCES billing_run (id),
    CONSTRAINT fk_billing_run_item_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT fk_billing_run_item_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT uq_billing_run_item_run_tenancy UNIQUE (billing_run_id, tenancy_id),
    CONSTRAINT chk_billing_run_item_status CHECK (
        status IN ('PENDING', 'COMPLETED', 'FAILED', 'SKIPPED')
    )
);

CREATE INDEX idx_billing_run_item_status ON billing_run_item (status);
CREATE INDEX idx_billing_run_item_run_id ON billing_run_item (billing_run_id);
