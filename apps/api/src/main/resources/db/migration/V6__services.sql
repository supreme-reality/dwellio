-- V6: services catalog, config, enrollment, charges (DB Schema v5.0 §6 / v1.5 §§6.1–6.4)
-- Also adds deferred FK from move_in_service.service_id.

CREATE TABLE service (
    id                  UUID PRIMARY KEY,
    property_id         UUID         NOT NULL,
    name                VARCHAR(160) NOT NULL,
    billing_type        VARCHAR(20)  NOT NULL,
    billing_timing      VARCHAR(20)  NOT NULL,
    mandatory           BOOLEAN      NOT NULL,
    proration_setting   VARCHAR(30)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_service_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT chk_service_billing_type CHECK (billing_type IN ('FIXED', 'VARIABLE')),
    CONSTRAINT chk_service_billing_timing CHECK (billing_timing IN ('UPFRONT', 'MONTHLY_ARREARS')),
    CONSTRAINT chk_service_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uq_service_property_name UNIQUE (property_id, name)
);

CREATE INDEX idx_service_property_id ON service (property_id);

CREATE TABLE service_config (
    id               UUID PRIMARY KEY,
    service_id       UUID           NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    effective_from   DATE           NOT NULL,
    effective_to     DATE,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_service_config_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT chk_service_config_amount CHECK (amount >= 0),
    CONSTRAINT chk_service_config_range CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE INDEX idx_service_config_service_id ON service_config (service_id);

CREATE TABLE service_enrollment (
    id           UUID PRIMARY KEY,
    tenancy_id   UUID         NOT NULL,
    service_id   UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    started_at   DATE         NOT NULL,
    ended_at     DATE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_service_enrollment_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT fk_service_enrollment_service
        FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT chk_service_enrollment_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT chk_service_enrollment_ended CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL)
        OR (status = 'ENDED' AND ended_at IS NOT NULL)
    )
);

-- Tenancy-level only: no bed_id / occupancy_id columns.
CREATE INDEX idx_service_enrollment_tenancy_id ON service_enrollment (tenancy_id);
CREATE INDEX idx_service_enrollment_service_id ON service_enrollment (service_id);
CREATE UNIQUE INDEX uq_service_enrollment_active
    ON service_enrollment (tenancy_id, service_id)
    WHERE status = 'ACTIVE';

CREATE TABLE service_charge (
    id                      UUID PRIMARY KEY,
    service_enrollment_id   UUID           NOT NULL,
    billing_period          DATE           NOT NULL,
    amount                  NUMERIC(12, 2) NOT NULL,
    note                    TEXT,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_service_charge_enrollment
        FOREIGN KEY (service_enrollment_id) REFERENCES service_enrollment (id),
    CONSTRAINT uq_service_charge_period UNIQUE (service_enrollment_id, billing_period),
    CONSTRAINT chk_service_charge_amount CHECK (amount >= 0)
);

CREATE INDEX idx_service_charge_enrollment_id ON service_charge (service_enrollment_id);

ALTER TABLE move_in_service
    ADD CONSTRAINT fk_move_in_service_service
        FOREIGN KEY (service_id) REFERENCES service (id);
