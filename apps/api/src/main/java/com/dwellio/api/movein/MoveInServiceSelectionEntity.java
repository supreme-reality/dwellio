package com.dwellio.api.movein;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "move_in_service")
public class MoveInServiceSelectionEntity {

    @Id
    private UUID id;

    @Column(name = "move_in_id", nullable = false)
    private UUID moveInId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "selected_amount", precision = 12, scale = 2)
    private BigDecimal selectedAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MoveInServiceSelectionEntity() {
    }

    public MoveInServiceSelectionEntity(
            UUID id,
            UUID moveInId,
            UUID serviceId,
            BigDecimal selectedAmount,
            Instant createdAt
    ) {
        this.id = id;
        this.moveInId = moveInId;
        this.serviceId = serviceId;
        this.selectedAmount = selectedAmount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMoveInId() {
        return moveInId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public BigDecimal getSelectedAmount() {
        return selectedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
