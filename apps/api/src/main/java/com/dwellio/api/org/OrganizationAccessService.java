package com.dwellio.api.org;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrganizationAccessService {

    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationAccessService(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public OrganizationMembershipEntity requireActiveMembership(UUID organizationId, UUID userId) {
        return membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Organization not found"
                ));
    }

    public OrganizationMembershipEntity requireOwner(UUID organizationId, UUID userId) {
        OrganizationMembershipEntity membership = requireActiveMembership(organizationId, userId);
        if (!"OWNER".equals(membership.getRole())) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "Owner role required"
            );
        }
        return membership;
    }
}
