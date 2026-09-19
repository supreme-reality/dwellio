package com.dwellio.api.movein;

import java.math.BigDecimal;
import java.util.UUID;

public record MoveInServiceSelectionResponse(
        UUID serviceId,
        BigDecimal selectedAmount
) {
}
