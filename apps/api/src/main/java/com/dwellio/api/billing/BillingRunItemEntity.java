package com.dwellio.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_run_item")
public class BillingRunItemEntity {

    @Id
    private UUID id;

    @Column(name = "billing_run_id", nullable = false)
    private UUID billingRunId;

    @Column(name = "tenancy_id", nullable = false)
    private UUID tenancyId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BillingRunItemEntity() {
    }

    public BillingRunItemEntity(
            UUID id,
            UUID billingRunId,
            UUID tenancyId,
            String status,
            UUID invoiceId,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.billingRunId = billingRunId;
        this.tenancyId = tenancyId;
        this.status = status;
        this.invoiceId = invoiceId;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBillingRunId() {
        return billingRunId;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
