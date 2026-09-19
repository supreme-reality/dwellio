package com.dwellio.api.inventory;

import java.util.UUID;

public record BedResponse(
        UUID id,
        UUID roomId,
        String name,
        String status,
        String availability,
        String blockReason
) {
}
