-- V1: identity, organization & access (DB Schema v5.0 §§2.1–2.4)
-- property_membership.property_id FK to property is deferred until the property migration.

CREATE TABLE organization (
    id          UUID PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_organization_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE app_user (
    id          UUID PRIMARY KEY,
    email       VARCHAR(320) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE organization_membership (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_org_membership_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_org_membership_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_org_membership_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT chk_org_membership_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT chk_org_membership_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE property_membership (
    id                          UUID PRIMARY KEY,
    organization_membership_id  UUID         NOT NULL,
    property_id                 UUID         NOT NULL,
    role                        VARCHAR(20)  NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_property_membership_org_membership
        FOREIGN KEY (organization_membership_id) REFERENCES organization_membership (id),
    CONSTRAINT uq_property_membership_member_property
        UNIQUE (organization_membership_id, property_id),
    CONSTRAINT chk_property_membership_role CHECK (role IN ('MANAGER'))
);

CREATE INDEX idx_org_membership_organization_id ON organization_membership (organization_id);
CREATE INDEX idx_org_membership_user_id ON organization_membership (user_id);
CREATE INDEX idx_property_membership_property_id ON property_membership (property_id);
CREATE INDEX idx_property_membership_org_membership_id ON property_membership (organization_membership_id);
