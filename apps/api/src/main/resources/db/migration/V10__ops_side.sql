-- V10: ops side features — expense, notice, document, ticket (DB Schema v5.0 §12 + CANONICAL locks)

CREATE TABLE expense_type (
    id           UUID PRIMARY KEY,
    property_id  UUID         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_expense_type_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT uq_expense_type_property_name UNIQUE (property_id, name)
);

CREATE INDEX idx_expense_type_property_id ON expense_type (property_id);

CREATE TABLE expense (
    id                   UUID PRIMARY KEY,
    property_id          UUID           NOT NULL,
    expense_type_id      UUID           NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             CHAR(3)        NOT NULL,
    incurred_on          DATE           NOT NULL,
    notes                TEXT,
    created_by_user_id   UUID           NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_expense_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT fk_expense_type
        FOREIGN KEY (expense_type_id) REFERENCES expense_type (id),
    CONSTRAINT fk_expense_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (id),
    CONSTRAINT chk_expense_amount CHECK (amount > 0)
);

CREATE INDEX idx_expense_property_id ON expense (property_id);
CREATE INDEX idx_expense_type_id ON expense (expense_type_id);
CREATE INDEX idx_expense_incurred_on ON expense (property_id, incurred_on);

CREATE TABLE notice (
    id            UUID PRIMARY KEY,
    property_id   UUID         NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    published_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_notice_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT chk_notice_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_notice_property_id ON notice (property_id);

CREATE TABLE document (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL,
    property_id       UUID,
    tenant_id         UUID,
    tenancy_id        UUID,
    name              VARCHAR(255) NOT NULL,
    storage_key       TEXT         NOT NULL,
    content_type      VARCHAR(120),
    size_bytes        BIGINT,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_document_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_document_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT fk_document_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_document_tenancy
        FOREIGN KEY (tenancy_id) REFERENCES tenancy (id),
    CONSTRAINT chk_document_status CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED'))
);

CREATE INDEX idx_document_organization_id ON document (organization_id);
CREATE INDEX idx_document_property_id ON document (property_id);

CREATE TABLE ticket (
    id                    UUID PRIMARY KEY,
    property_id           UUID         NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    body                  TEXT,
    status                VARCHAR(20)  NOT NULL,
    created_by_user_id    UUID         NOT NULL,
    assigned_to_user_id   UUID,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_ticket_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT fk_ticket_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_ticket_assigned_to
        FOREIGN KEY (assigned_to_user_id) REFERENCES app_user (id),
    CONSTRAINT chk_ticket_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_ticket_property_id ON ticket (property_id);
CREATE INDEX idx_ticket_assigned_to ON ticket (assigned_to_user_id);
