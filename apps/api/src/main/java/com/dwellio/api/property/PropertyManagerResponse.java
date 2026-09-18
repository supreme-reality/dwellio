package com.dwellio.api.property;

import java.util.UUID;

public record PropertyManagerResponse(
        UUID propertyMembershipId,
        UUID userId,
        String email,
        String name,
        String role
) {
}
