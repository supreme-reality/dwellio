package com.dwellio.api.org;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public OrganizationResponse create(AppUserEntity user, CreateOrganizationRequest request) {
        Instant now = Instant.now();
        UUID organizationId = UUID.randomUUID();
        String name = request.name().trim();

        OrganizationEntity organization = organizationRepository.save(
                new OrganizationEntity(organizationId, name, "ACTIVE", now, now)
        );

        OrganizationMembershipEntity membership = membershipRepository.save(
                new OrganizationMembershipEntity(
                        UUID.randomUUID(),
                        organizationId,
                        user.getId(),
                        "OWNER",
                        "ACTIVE",
                        now,
                        now
                )
        );

        return toResponse(organization, membership.getRole());
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForUser(AppUserEntity user) {
        List<OrganizationMembershipEntity> memberships = membershipRepository.findActiveByUserId(user.getId());
        return memberships.stream()
                .map(membership -> {
                    OrganizationEntity organization = organizationRepository.findById(membership.getOrganizationId())
                            .orElseThrow(() -> new ApiException(
                                    ErrorCode.INTERNAL_ERROR,
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Organization missing for membership"
                            ));
                    return toResponse(organization, membership.getRole());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getForUser(AppUserEntity user, UUID organizationId) {
        OrganizationMembershipEntity membership = membershipRepository
                .findActiveByOrganizationIdAndUserId(organizationId, user.getId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Organization not found"
                ));

        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Organization not found"
                ));

        return toResponse(organization, membership.getRole());
    }

    private static OrganizationResponse toResponse(OrganizationEntity organization, String role) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getStatus(),
                role
        );
    }
}
