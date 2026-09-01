package com.dwellio.api.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice")
public class InvoiceEntity {

    @Id
    private UUID id;

    @Column(name = "tenancy_id", nullable = false)
    private UUID tenancyId;

    @Column(name = "invoice_type", nullable = false, length = 20)
    private String invoiceType;

    @Column(name = "billing_period")
    private LocalDate billingPeriod;

    @Column(name = "billing_date", nullable = false)
    private LocalDate billingDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InvoiceEntity() {
    }

    public InvoiceEntity(
            UUID id,
            UUID tenancyId,
            String invoiceType,
            LocalDate billingPeriod,
            LocalDate billingDate,
            LocalDate dueDate,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal total,
            Instant finalizedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenancyId = tenancyId;
        this.invoiceType = invoiceType;
        this.billingPeriod = billingPeriod;
        this.billingDate = billingDate;
        this.dueDate = dueDate;
        this.status = status;
        this.currency = currency;
        this.subtotal = subtotal;
        this.total = total;
        this.finalizedAt = finalizedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public String getInvoiceType() {
        return invoiceType;
    }

    public LocalDate getBillingPeriod() {
        return billingPeriod;
    }

    public LocalDate getBillingDate() {
        return billingDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
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
