package com.dwellio.api.property;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.org.OrganizationMembershipEntity;
import com.dwellio.api.org.OrganizationMembershipRepository;
import com.dwellio.api.ticket.TicketService;
import com.dwellio.api.user.AppUserEntity;
import com.dwellio.api.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PropertyManagerService {

    private final PropertyAccessService propertyAccessService;
    private final PropertyMembershipRepository propertyMembershipRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final AppUserRepository appUserRepository;
    private final TicketService ticketService;

    public PropertyManagerService(
            PropertyAccessService propertyAccessService,
            PropertyMembershipRepository propertyMembershipRepository,
            OrganizationMembershipRepository organizationMembershipRepository,
            AppUserRepository appUserRepository,
            TicketService ticketService
    ) {
        this.propertyAccessService = propertyAccessService;
        this.propertyMembershipRepository = propertyMembershipRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.appUserRepository = appUserRepository;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public List<PropertyManagerResponse> list(AppUserEntity caller, UUID propertyId) {
        propertyAccessService.requireOwnerOfProperty(caller, propertyId);
        return propertyMembershipRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PropertyManagerResponse assign(AppUserEntity caller, UUID propertyId, AssignPropertyManagerRequest request) {
        PropertyEntity property = propertyAccessService.requireOwnerOfProperty(caller, propertyId);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUserEntity assignee = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "User not found for email"
                ));

        OrganizationMembershipEntity orgMembership = organizationMembershipRepository
                .findActiveByOrganizationIdAndUserId(property.getOrganizationId(), assignee.getId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Assignee must be an active organization MEMBER"
                ));

        if (!"MEMBER".equals(orgMembership.getRole())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only organization MEMBER roles can be assigned as property managers"
            );
        }

        if (propertyMembershipRepository
                .findByOrganizationMembershipIdAndPropertyId(orgMembership.getId(), propertyId)
                .isPresent()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "User is already a manager of this property"
            );
        }

        Instant now = Instant.now();
        PropertyMembershipEntity membership = propertyMembershipRepository.save(
                new PropertyMembershipEntity(
                        UUID.randomUUID(),
                        orgMembership.getId(),
                        propertyId,
                        "MANAGER",
                        now,
                        now
                )
        );
        return toResponse(membership);
    }

    @Transactional
    public void remove(AppUserEntity caller, UUID propertyId, UUID propertyMembershipId) {
        propertyAccessService.requireOwnerOfProperty(caller, propertyId);
        PropertyMembershipEntity membership = propertyMembershipRepository
                .findByIdAndPropertyId(propertyMembershipId, propertyId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Property manager assignment not found"
                ));
        OrganizationMembershipEntity orgMembership = organizationMembershipRepository
                .findById(membership.getOrganizationMembershipId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Organization membership missing for property manager"
                ));
        ticketService.unassignOpenTicketsOnProperty(orgMembership.getUserId(), propertyId);
        propertyMembershipRepository.delete(membership);
    }

    private PropertyManagerResponse toResponse(PropertyMembershipEntity membership) {
        OrganizationMembershipEntity orgMembership = organizationMembershipRepository
                .findById(membership.getOrganizationMembershipId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Organization membership missing for property manager"
                ));
        AppUserEntity user = appUserRepository.findById(orgMembership.getUserId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "User missing for property manager"
                ));
        return new PropertyManagerResponse(
                membership.getId(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                membership.getRole()
        );
    }
}
