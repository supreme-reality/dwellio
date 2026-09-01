package com.dwellio.api.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "tenancy_id")
    private UUID tenancyId;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @Column(name = "bank_transfer_reference", length = 200)
    private String bankTransferReference;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    /**
     * Deposit to receipt on confirm/activation (move-in façade). Null when not applicable.
     */
    @Column(name = "intended_deposit_amount", precision = 12, scale = 2)
    private BigDecimal intendedDepositAmount;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            UUID id,
            UUID organizationId,
            UUID tenancyId,
            String paymentMethod,
            BigDecimal amount,
            String currency,
            String status,
            String externalReference,
            String bankTransferReference,
            String idempotencyKey,
            BigDecimal intendedDepositAmount,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.tenancyId = tenancyId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.externalReference = externalReference;
        this.bankTransferReference = bankTransferReference;
        this.idempotencyKey = idempotencyKey;
        this.intendedDepositAmount = intendedDepositAmount;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getBankTransferReference() {
        return bankTransferReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getIntendedDepositAmount() {
        return intendedDepositAmount;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
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
