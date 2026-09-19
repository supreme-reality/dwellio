package com.dwellio.api.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "settlement_snapshot")
public class SettlementSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "tenancy_id", nullable = false)
    private UUID tenancyId;

    @Column(name = "occupancy_id", nullable = false)
    private UUID occupancyId;

    @Column(name = "checkout_date", nullable = false)
    private LocalDate checkoutDate;

    @Column(name = "outstanding_receivable", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingReceivable;

    @Column(name = "new_checkout_charges", nullable = false, precision = 12, scale = 2)
    private BigDecimal newCheckoutCharges;

    @Column(name = "deposit_balance_before", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositBalanceBefore;

    @Column(name = "deposit_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositDeduction;

    @Column(name = "deposit_refund_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositRefundDue;

    @Column(name = "total_receivable", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalReceivable;

    @Column(name = "net_receivable", nullable = false, precision = 12, scale = 2)
    private BigDecimal netReceivable;

    @Column(name = "refund_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundDue;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SettlementSnapshotEntity() {
    }

    public SettlementSnapshotEntity(
            UUID id,
            UUID tenancyId,
            UUID occupancyId,
            LocalDate checkoutDate,
            BigDecimal outstandingReceivable,
            BigDecimal newCheckoutCharges,
            BigDecimal depositBalanceBefore,
            BigDecimal depositDeduction,
            BigDecimal depositRefundDue,
            BigDecimal totalReceivable,
            BigDecimal netReceivable,
            BigDecimal refundDue,
            String currency,
            String status,
            Instant confirmedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.tenancyId = tenancyId;
        this.occupancyId = occupancyId;
        this.checkoutDate = checkoutDate;
        this.outstandingReceivable = outstandingReceivable;
        this.newCheckoutCharges = newCheckoutCharges;
        this.depositBalanceBefore = depositBalanceBefore;
        this.depositDeduction = depositDeduction;
        this.depositRefundDue = depositRefundDue;
        this.totalReceivable = totalReceivable;
        this.netReceivable = netReceivable;
        this.refundDue = refundDue;
        this.currency = currency;
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public UUID getOccupancyId() {
        return occupancyId;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public BigDecimal getOutstandingReceivable() {
        return outstandingReceivable;
    }

    public BigDecimal getNewCheckoutCharges() {
        return newCheckoutCharges;
    }

    public BigDecimal getDepositBalanceBefore() {
        return depositBalanceBefore;
    }

    public BigDecimal getDepositDeduction() {
        return depositDeduction;
    }

    public BigDecimal getDepositRefundDue() {
        return depositRefundDue;
    }

    public BigDecimal getTotalReceivable() {
        return totalReceivable;
    }

    public BigDecimal getNetReceivable() {
        return netReceivable;
    }

    public BigDecimal getRefundDue() {
        return refundDue;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
