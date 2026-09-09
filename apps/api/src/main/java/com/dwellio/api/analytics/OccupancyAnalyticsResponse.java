package com.dwellio.api.analytics;

import java.math.BigDecimal;

public record OccupancyAnalyticsResponse(
        long totalBeds,
        long occupiedBeds,
        long blockedBeds,
        BigDecimal occupancyRate
) {
}
