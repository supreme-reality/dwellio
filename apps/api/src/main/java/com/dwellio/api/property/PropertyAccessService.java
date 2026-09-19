package com.dwellio.api.property;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.org.OrganizationAccessService;
import com.dwellio.api.org.OrganizationMembershipEntity;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PropertyAccessService {

    private final PropertyRepository propertyRepository;
    private final PropertyMembershipRepository propertyMembershipRepository;
    private final OrganizationAccessService organizationAccessService;

    public PropertyAccessService(
            PropertyRepository propertyRepository,
            PropertyMembershipRepository propertyMembershipRepository,
            OrganizationAccessService organizationAccessService
    ) {
        this.propertyRepository = propertyRepository;
        this.propertyMembershipRepository = propertyMembershipRepository;
        this.organizationAccessService = organizationAccessService;
    }

    public PropertyEntity requireActiveProperty(UUID propertyId) {
        return propertyRepository.findActiveById(propertyId)
                .orElseThrow(() -> notFound());
    }

    public PropertyEntity requireReadableProperty(AppUserEntity user, UUID propertyId) {
        PropertyEntity property = requireActiveProperty(propertyId);
        OrganizationMembershipEntity membership = organizationAccessService
                .requireActiveMembership(property.getOrganizationId(), user.getId());

        if ("OWNER".equals(membership.getRole())) {
            return property;
        }

        if (propertyMembershipRepository.existsActiveManagerAssignment(propertyId, user.getId())) {
            return property;
        }

        throw notFound();
    }

    public PropertyEntity requireOwnerOfProperty(AppUserEntity user, UUID propertyId) {
        PropertyEntity property = requireActiveProperty(propertyId);
        organizationAccessService.requireOwner(property.getOrganizationId(), user.getId());
        return property;
    }

    public boolean canMutateInventory(AppUserEntity user, PropertyEntity property) {
        OrganizationMembershipEntity membership = organizationAccessService
                .requireActiveMembership(property.getOrganizationId(), user.getId());
        if ("OWNER".equals(membership.getRole())) {
            return true;
        }
        return propertyMembershipRepository.existsActiveManagerAssignment(property.getId(), user.getId());
    }

    /**
     * Owner or assigned Manager may mutate inventory.
     * Org members without assignment get 403 (not 404) so scope denials are explicit on writes.
     */
    public PropertyEntity requireInventoryMutator(AppUserEntity user, UUID propertyId) {
        PropertyEntity property = requireActiveProperty(propertyId);
        OrganizationMembershipEntity membership = organizationAccessService
                .requireActiveMembership(property.getOrganizationId(), user.getId());
        if ("OWNER".equals(membership.getRole())) {
            return property;
        }
        if (propertyMembershipRepository.existsActiveManagerAssignment(propertyId, user.getId())) {
            return property;
        }
        throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Property access required");
    }

    public void requireInventoryMutator(AppUserEntity user, PropertyEntity property) {
        requireInventoryMutator(user, property.getId());
    }

    /**
     * Owner, or MEMBER with at least one manager assignment in the org, may read org-scoped tenants.
     * Callers should map false / membership miss to NOT_FOUND for the resource.
     */
    public boolean canReadAnyPropertyInOrg(AppUserEntity user, UUID organizationId) {
        OrganizationMembershipEntity membership;
        try {
            membership = organizationAccessService.requireActiveMembership(organizationId, user.getId());
        } catch (ApiException ex) {
            return false;
        }
        if ("OWNER".equals(membership.getRole())) {
            return true;
        }
        return propertyMembershipRepository.existsActiveManagerAssignmentInOrg(organizationId, user.getId());
    }

    /**
     * Owner or assigned Manager may mutate org-scoped tenants (same bar as inventory writes).
     * Returns false when the user is not an org member or has no assignment.
     */
    public boolean canMutateAnyPropertyInOrg(AppUserEntity user, UUID organizationId) {
        OrganizationMembershipEntity membership;
        try {
            membership = organizationAccessService.requireActiveMembership(organizationId, user.getId());
        } catch (ApiException ex) {
            return false;
        }
        if ("OWNER".equals(membership.getRole())) {
            return true;
        }
        return propertyMembershipRepository.existsActiveManagerAssignmentInOrg(organizationId, user.getId());
    }

    public boolean isOrgMember(AppUserEntity user, UUID organizationId) {
        try {
            organizationAccessService.requireActiveMembership(organizationId, user.getId());
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Property not found");
    }
}
