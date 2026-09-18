-- V3: effective-dated rent configuration (DB Schema v5.0 §5)

CREATE TABLE rent_config (
    id               UUID PRIMARY KEY,
    property_id      UUID           NOT NULL,
    room_id          UUID,
    bed_id           UUID,
    amount           NUMERIC(12, 2) NOT NULL,
    effective_from   DATE           NOT NULL,
    effective_to     DATE,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_rent_config_property
        FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT fk_rent_config_room
        FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_rent_config_bed
        FOREIGN KEY (bed_id) REFERENCES bed (id),
    CONSTRAINT chk_rent_config_amount CHECK (amount >= 0),
    CONSTRAINT chk_rent_config_target CHECK (
        (room_id IS NULL AND bed_id IS NULL)
        OR (room_id IS NOT NULL AND bed_id IS NULL)
        OR (room_id IS NOT NULL AND bed_id IS NOT NULL)
    ),
    CONSTRAINT chk_rent_config_range CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE INDEX idx_rent_config_property_id ON rent_config (property_id);
CREATE INDEX idx_rent_config_room_id ON rent_config (room_id);
CREATE INDEX idx_rent_config_bed_id ON rent_config (bed_id);
