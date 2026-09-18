package com.dwellio.api.inventory;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
}
