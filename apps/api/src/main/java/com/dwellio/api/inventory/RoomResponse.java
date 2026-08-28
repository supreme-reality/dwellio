package com.dwellio.api.inventory;

import java.util.UUID;

public record RoomResponse(
        UUID id,
        UUID propertyId,
        String name,
        String status
) {
}
