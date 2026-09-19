-- V4: tenant & stay lifecycle (DB Schema v5.0 §§4.1–4.3, §§7.1–7.2)
-- move_in_service.service_id FK to service is deferred until the services migration (V6).

CREATE TABLE tenant (
    id                         UUID PRIMARY KEY,
    organization_id            UUID         NOT NULL,
    first_name                 VARCHAR(100) NOT NULL,
    last_name                  VARCHAR(100),
    phone                      VARCHAR(30)  NOT NULL,
    email                      VARCHAR(320),
    date_of_birth              DATE,
    gender                     VARCHAR(40),
    address                    TEXT,
    emergency_contact_name     VARCHAR(160),
    emergency_contact_phone    VARCHAR(30),
    government_id              VARCHAR(100),
    notes                      TEXT,
    status                     VARCHAR(20)  NOT NULL,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_tenant_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_tenant_org_phone UNIQUE (organization_id, phone),
    CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_tenant_org_email
    ON tenant (organization_id, email)
    WHERE email IS NOT NULL;

CREATE TABLE tenancy (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    property_id  UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_tenancy_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_tenancy_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT chk_tenancy_status CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'CANCELLED'))
);

CREATE TABLE occupancy (
    id           UUID PRIMARY KEY,
    tenancy_id   UUID         NOT NULL,
    bed_id       UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL,
    ended_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_occupancy_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT fk_occupancy_bed
        FOREIGN KEY (bed_id) REFERENCES bed (id),
    CONSTRAINT chk_occupancy_status CHECK (status IN ('ACTIVE', 'COMPLETED'))
);

CREATE UNIQUE INDEX uq_occupancy_active_tenancy
    ON occupancy (tenancy_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_occupancy_active_bed
    ON occupancy (bed_id)
    WHERE status = 'ACTIVE';

CREATE TABLE move_in (
    id             UUID PRIMARY KEY,
    tenancy_id     UUID         NOT NULL,
    bed_id         UUID         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    move_in_date   DATE         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_move_in_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT fk_move_in_bed
        FOREIGN KEY (bed_id) REFERENCES bed (id),
    CONSTRAINT chk_move_in_status CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE move_in_service (
    id                UUID PRIMARY KEY,
    move_in_id        UUID           NOT NULL,
    service_id        UUID           NOT NULL,
    selected_amount   NUMERIC(12, 2),
    created_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_move_in_service_move_in
        FOREIGN KEY (move_in_id) REFERENCES move_in (id),
    CONSTRAINT uq_move_in_service UNIQUE (move_in_id, service_id)
    -- FK to service deferred until V6
);

CREATE INDEX idx_tenant_organization_id ON tenant (organization_id);
CREATE INDEX idx_tenancy_tenant_id ON tenancy (tenant_id);
CREATE INDEX idx_tenancy_property_id ON tenancy (property_id);
CREATE INDEX idx_occupancy_tenancy_id ON occupancy (tenancy_id);
CREATE INDEX idx_occupancy_bed_id ON occupancy (bed_id);
CREATE INDEX idx_move_in_tenancy_id ON move_in (tenancy_id);
CREATE INDEX idx_move_in_bed_id ON move_in (bed_id);
CREATE INDEX idx_move_in_service_move_in_id ON move_in_service (move_in_id);
