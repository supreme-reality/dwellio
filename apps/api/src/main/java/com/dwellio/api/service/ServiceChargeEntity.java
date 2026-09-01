package com.dwellio.api.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "service_charge")
public class ServiceChargeEntity {

    @Id
    private UUID id;

    @Column(name = "service_enrollment_id", nullable = false)
    private UUID serviceEnrollmentId;

    @Column(name = "billing_period", nullable = false)
    private LocalDate billingPeriod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceChargeEntity() {
    }

    public ServiceChargeEntity(
            UUID id,
            UUID serviceEnrollmentId,
            LocalDate billingPeriod,
            BigDecimal amount,
            String note,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.serviceEnrollmentId = serviceEnrollmentId;
        this.billingPeriod = billingPeriod;
        this.amount = amount;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceEnrollmentId() {
        return serviceEnrollmentId;
    }

    public LocalDate getBillingPeriod() {
        return billingPeriod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
