package com.dwellio.api.property;

import com.dwellio.api.org.OrganizationAccessService;
import com.dwellio.api.org.OrganizationMembershipEntity;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final OrganizationAccessService organizationAccessService;
    private final PropertyAccessService propertyAccessService;

    public PropertyService(
            PropertyRepository propertyRepository,
            OrganizationAccessService organizationAccessService,
            PropertyAccessService propertyAccessService
    ) {
        this.propertyRepository = propertyRepository;
        this.organizationAccessService = organizationAccessService;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional
    public PropertyResponse create(AppUserEntity user, UUID organizationId, CreatePropertyRequest request) {
        organizationAccessService.requireOwner(organizationId, user.getId());
        Instant now = Instant.now();
        PropertyEntity property = propertyRepository.save(new PropertyEntity(
                UUID.randomUUID(),
                organizationId,
                request.name().trim(),
                blankToNull(request.address()),
                "ACTIVE",
                request.paymentDueDays(),
                request.defaultCurrency().toUpperCase(Locale.ROOT),
                now,
                now
        ));
        return toResponse(property);
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> listVisible(AppUserEntity user, UUID organizationId) {
        OrganizationMembershipEntity membership =
                organizationAccessService.requireActiveMembership(organizationId, user.getId());

        List<PropertyEntity> properties = "OWNER".equals(membership.getRole())
                ? propertyRepository.findActiveByOrganizationId(organizationId)
                : propertyRepository.findActiveAssignedToUser(organizationId, user.getId());

        return properties.stream().map(PropertyService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(AppUserEntity user, UUID propertyId) {
        return toResponse(propertyAccessService.requireReadableProperty(user, propertyId));
    }

    @Transactional
    public PropertyResponse update(AppUserEntity user, UUID propertyId, UpdatePropertyRequest request) {
        PropertyEntity property = propertyAccessService.requireOwnerOfProperty(user, propertyId);

        if (request.name() != null && !request.name().isBlank()) {
            property.setName(request.name().trim());
        }
        if (request.address() != null) {
            property.setAddress(blankToNull(request.address()));
        }
        if (request.paymentDueDays() != null) {
            property.setPaymentDueDays(request.paymentDueDays());
        }
        if (request.defaultCurrency() != null) {
            property.setDefaultCurrency(request.defaultCurrency().toUpperCase(Locale.ROOT));
        }
        property.setUpdatedAt(Instant.now());
        return toResponse(propertyRepository.save(property));
    }

    @Transactional
    public void softDelete(AppUserEntity user, UUID organizationId, UUID propertyId) {
        organizationAccessService.requireOwner(organizationId, user.getId());
        PropertyEntity property = propertyRepository.findById(propertyId)
                .filter(p -> p.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new com.dwellio.api.common.ApiException(
                        com.dwellio.api.common.ErrorCode.NOT_FOUND,
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Property not found"
                ));
        property.setStatus("INACTIVE");
        property.setUpdatedAt(Instant.now());
        propertyRepository.save(property);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static PropertyResponse toResponse(PropertyEntity property) {
        return new PropertyResponse(
                property.getId(),
                property.getOrganizationId(),
                property.getName(),
                property.getAddress(),
                property.getStatus(),
                property.getPaymentDueDays(),
                property.getDefaultCurrency()
        );
    }
}
