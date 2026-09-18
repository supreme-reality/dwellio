package com.dwellio.api.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property")
public class PropertyEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column
    private String address;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "payment_due_days", nullable = false)
    private int paymentDueDays;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PropertyEntity() {
    }

    public PropertyEntity(
            UUID id,
            UUID organizationId,
            String name,
            String address,
            String status,
            int paymentDueDays,
            String defaultCurrency,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.address = address;
        this.status = status;
        this.paymentDueDays = paymentDueDays;
        this.defaultCurrency = defaultCurrency;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPaymentDueDays() {
        return paymentDueDays;
    }

    public void setPaymentDueDays(int paymentDueDays) {
        this.paymentDueDays = paymentDueDays;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
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
