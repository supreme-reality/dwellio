package com.dwellio.api.org;

import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String status,
        String role
) {
}
