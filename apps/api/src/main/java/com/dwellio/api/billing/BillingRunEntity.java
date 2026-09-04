package com.dwellio.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "billing_run")
public class BillingRunEntity {

    @Id
    private UUID id;

    @Column(name = "billing_period", nullable = false)
    private LocalDate billingPeriod;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BillingRunEntity() {
    }

    public BillingRunEntity(UUID id, LocalDate billingPeriod, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.billingPeriod = billingPeriod;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getBillingPeriod() {
        return billingPeriod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
