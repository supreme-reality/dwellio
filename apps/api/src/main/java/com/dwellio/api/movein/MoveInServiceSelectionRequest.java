package com.dwellio.api.movein;

import java.math.BigDecimal;
import java.util.UUID;

public record MoveInServiceSelectionRequest(
        UUID serviceId,
        BigDecimal selectedAmount
) {
}
