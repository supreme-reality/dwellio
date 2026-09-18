package com.dwellio.api.property;

import java.util.UUID;

public record PropertyResponse(
        UUID id,
        UUID organizationId,
        String name,
        String address,
        String status,
        int paymentDueDays,
        String defaultCurrency
) {
}
