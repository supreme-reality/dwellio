package com.dwellio.api.movein;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "move_in")
public class MoveInEntity {

    @Id
    private UUID id;

    @Column(name = "tenancy_id", nullable = false)
    private UUID tenancyId;

    @Column(name = "bed_id", nullable = false)
    private UUID bedId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MoveInEntity() {
    }

    public MoveInEntity(
            UUID id,
            UUID tenancyId,
            UUID bedId,
            String status,
            LocalDate moveInDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenancyId = tenancyId;
        this.bedId = bedId;
        this.status = status;
        this.moveInDate = moveInDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public UUID getBedId() {
        return bedId;
    }

    public void setBedId(UUID bedId) {
        this.bedId = bedId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getMoveInDate() {
        return moveInDate;
    }

    public void setMoveInDate(LocalDate moveInDate) {
        this.moveInDate = moveInDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
