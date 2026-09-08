package com.dwellio.api.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket")
public class TicketEntity {

    @Id
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketEntity() {
    }

    public TicketEntity(
            UUID id,
            UUID propertyId,
            String title,
            String body,
            String status,
            UUID createdByUserId,
            UUID assignedToUserId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.propertyId = propertyId;
        this.title = title;
        this.body = body;
        this.status = status;
        this.createdByUserId = createdByUserId;
        this.assignedToUserId = assignedToUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public UUID getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(UUID assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
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
