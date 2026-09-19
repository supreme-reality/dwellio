package com.dwellio.api.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "tenancy_id")
    private UUID tenancyId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(
            UUID id,
            UUID organizationId,
            UUID propertyId,
            UUID tenantId,
            UUID tenancyId,
            String name,
            String storageKey,
            String contentType,
            Long sizeBytes,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.propertyId = propertyId;
        this.tenantId = tenantId;
        this.tenancyId = tenancyId;
        this.name = name;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public String getName() {
        return name;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
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
