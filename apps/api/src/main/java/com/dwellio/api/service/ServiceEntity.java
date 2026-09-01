package com.dwellio.api.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service")
public class ServiceEntity {

    @Id
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "billing_type", nullable = false, length = 20)
    private String billingType;

    @Column(name = "billing_timing", nullable = false, length = 20)
    private String billingTiming;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(name = "proration_setting", nullable = false, length = 30)
    private String prorationSetting;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceEntity() {
    }

    public ServiceEntity(
            UUID id,
            UUID propertyId,
            String name,
            String billingType,
            String billingTiming,
            boolean mandatory,
            String prorationSetting,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.propertyId = propertyId;
        this.name = name;
        this.billingType = billingType;
        this.billingTiming = billingTiming;
        this.mandatory = mandatory;
        this.prorationSetting = prorationSetting;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBillingType() {
        return billingType;
    }

    public void setBillingType(String billingType) {
        this.billingType = billingType;
    }

    public String getBillingTiming() {
        return billingTiming;
    }

    public void setBillingTiming(String billingTiming) {
        this.billingTiming = billingTiming;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public String getProrationSetting() {
        return prorationSetting;
    }

    public void setProrationSetting(String prorationSetting) {
        this.prorationSetting = prorationSetting;
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
