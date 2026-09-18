-- V2: property inventory (DB Schema v5.0 §§3.1–3.3)
-- Also wires property_membership.property_id FK deferred from V1.

CREATE TABLE property (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL,
    name              VARCHAR(160) NOT NULL,
    address           TEXT,
    status            VARCHAR(20)  NOT NULL,
    payment_due_days  INTEGER      NOT NULL,
    default_currency  CHAR(3)      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_property_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT chk_property_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_property_payment_due_days CHECK (payment_due_days >= 0)
);

CREATE TABLE room (
    id           UUID PRIMARY KEY,
    property_id  UUID         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_room_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT uq_room_property_name UNIQUE (property_id, name),
    CONSTRAINT chk_room_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE bed (
    id                   UUID PRIMARY KEY,
    room_id              UUID         NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    block_reason         TEXT,
    blocked_at           TIMESTAMPTZ,
    blocked_by_user_id   UUID,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_bed_room
        FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_bed_blocked_by_user
        FOREIGN KEY (blocked_by_user_id) REFERENCES app_user (id),
    CONSTRAINT uq_bed_room_name UNIQUE (room_id, name),
    CONSTRAINT chk_bed_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

ALTER TABLE property_membership
    ADD CONSTRAINT fk_property_membership_property
        FOREIGN KEY (property_id) REFERENCES property (id);

CREATE INDEX idx_property_organization_id ON property (organization_id);
CREATE INDEX idx_room_property_id ON room (property_id);
CREATE INDEX idx_bed_room_id ON bed (room_id);
