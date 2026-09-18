package com.dwellio.api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RentConfigResponse(
        UUID id,
        UUID propertyId,
        UUID roomId,
        UUID bedId,
        BigDecimal amount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String level
) {
}
