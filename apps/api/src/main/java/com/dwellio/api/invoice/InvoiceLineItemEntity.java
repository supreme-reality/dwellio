package com.dwellio.api.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_item")
public class InvoiceLineItemEntity {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvoiceLineItemEntity() {
    }

    public InvoiceLineItemEntity(
            UUID id,
            UUID invoiceId,
            String lineType,
            String description,
            BigDecimal quantity,
            BigDecimal unitAmount,
            BigDecimal amount,
            UUID referenceId,
            Instant createdAt
    ) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.lineType = lineType;
        this.description = description;
        this.quantity = quantity;
        this.unitAmount = unitAmount;
        this.amount = amount;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getLineType() {
        return lineType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
