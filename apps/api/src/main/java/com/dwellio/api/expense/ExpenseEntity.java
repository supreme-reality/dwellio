package com.dwellio.api.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense")
public class ExpenseEntity {

    @Id
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "expense_type_id", nullable = false)
    private UUID expenseTypeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "incurred_on", nullable = false)
    private LocalDate incurredOn;

    @Column
    private String notes;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExpenseEntity() {
    }

    public ExpenseEntity(
            UUID id,
            UUID propertyId,
            UUID expenseTypeId,
            BigDecimal amount,
            String currency,
            LocalDate incurredOn,
            String notes,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.propertyId = propertyId;
        this.expenseTypeId = expenseTypeId;
        this.amount = amount;
        this.currency = currency;
        this.incurredOn = incurredOn;
        this.notes = notes;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getExpenseTypeId() {
        return expenseTypeId;
    }

    public void setExpenseTypeId(UUID expenseTypeId) {
        this.expenseTypeId = expenseTypeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getIncurredOn() {
        return incurredOn;
    }

    public void setIncurredOn(LocalDate incurredOn) {
        this.incurredOn = incurredOn;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
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
