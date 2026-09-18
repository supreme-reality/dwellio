package com.dwellio.api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ResolvedRentResponse(
        BigDecimal amount,
        String level,
        UUID rentConfigId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String currency
) {
}
