package com.dwellio.api.org;

import java.util.UUID;

public record OrganizationMemberResponse(
        UUID membershipId,
        UUID userId,
        String email,
        String name,
        String role,
        String status
) {
}
