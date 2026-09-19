package com.dwellio.api.org;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyMembershipEntity;
import com.dwellio.api.property.PropertyMembershipRepository;
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
public class OrganizationMembershipService {

    private final OrganizationAccessService accessService;
    private final OrganizationMembershipRepository membershipRepository;
    private final PropertyMembershipRepository propertyMembershipRepository;
    private final AppUserRepository appUserRepository;
    private final TicketService ticketService;

    public OrganizationMembershipService(
            OrganizationAccessService accessService,
            OrganizationMembershipRepository membershipRepository,
            PropertyMembershipRepository propertyMembershipRepository,
            AppUserRepository appUserRepository,
            TicketService ticketService
    ) {
        this.accessService = accessService;
        this.membershipRepository = membershipRepository;
        this.propertyMembershipRepository = propertyMembershipRepository;
        this.appUserRepository = appUserRepository;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> list(AppUserEntity caller, UUID organizationId) {
        accessService.requireActiveMembership(organizationId, caller.getId());
        return membershipRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrganizationMemberResponse add(AppUserEntity caller, UUID organizationId, AddOrganizationMemberRequest request) {
        accessService.requireOwner(organizationId, caller.getId());

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUserEntity invitee = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "User not found for email"
                ));

        Instant now = Instant.now();
        OrganizationMembershipEntity existing = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, invitee.getId())
                .orElse(null);

        if (existing != null) {
            if ("ACTIVE".equals(existing.getStatus())) {
                throw new ApiException(
                        ErrorCode.CONFLICT,
                        HttpStatus.CONFLICT,
                        "User is already a member of this organization"
                );
            }
            existing.setStatus("ACTIVE");
            existing.setUpdatedAt(now);
            return toResponse(membershipRepository.save(existing));
        }

        OrganizationMembershipEntity membership = membershipRepository.save(
                new OrganizationMembershipEntity(
                        UUID.randomUUID(),
                        organizationId,
                        invitee.getId(),
                        "MEMBER",
                        "ACTIVE",
                        now,
                        now
                )
        );
        return toResponse(membership);
    }

    @Transactional
    public OrganizationMemberResponse updateStatus(
            AppUserEntity caller,
            UUID organizationId,
            UUID membershipId,
            UpdateOrganizationMemberRequest request
    ) {
        accessService.requireOwner(organizationId, caller.getId());

        OrganizationMembershipEntity membership = membershipRepository
                .findByIdAndOrganizationId(membershipId, organizationId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Membership not found"
                ));

        if ("OWNER".equals(membership.getRole())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Owner membership status cannot be changed"
            );
        }

        if ("INACTIVE".equals(request.status()) && "ACTIVE".equals(membership.getStatus())) {
            List<PropertyMembershipEntity> assignments =
                    propertyMembershipRepository.findByOrganizationMembershipId(membership.getId());
            for (PropertyMembershipEntity assignment : assignments) {
                ticketService.unassignOpenTicketsOnProperty(membership.getUserId(), assignment.getPropertyId());
            }
            propertyMembershipRepository.deleteByOrganizationMembershipId(membership.getId());
        }

        membership.setStatus(request.status());
        membership.setUpdatedAt(Instant.now());
        return toResponse(membershipRepository.save(membership));
    }

    private OrganizationMemberResponse toResponse(OrganizationMembershipEntity membership) {
        AppUserEntity user = appUserRepository.findById(membership.getUserId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Membership user missing"
                ));
        return new OrganizationMemberResponse(
                membership.getId(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                membership.getRole(),
                membership.getStatus()
        );
    }
}
