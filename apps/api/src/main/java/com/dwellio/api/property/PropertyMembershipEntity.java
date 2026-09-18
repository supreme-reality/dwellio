package com.dwellio.api.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property_membership")
public class PropertyMembershipEntity {

    @Id
    private UUID id;

    @Column(name = "organization_membership_id", nullable = false)
    private UUID organizationMembershipId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PropertyMembershipEntity() {
    }

    public PropertyMembershipEntity(
            UUID id,
            UUID organizationMembershipId,
            UUID propertyId,
            String role,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.organizationMembershipId = organizationMembershipId;
        this.propertyId = propertyId;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationMembershipId() {
        return organizationMembershipId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
