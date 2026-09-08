package com.dwellio.api.ticket;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateTicketRequest(
        @Size(max = 200) String title,
        String body,
        @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status,
        UUID assignedToUserId,
        Boolean clearAssignee
) {
}
