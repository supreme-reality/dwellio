package com.dwellio.api.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String title,
        String body,
        UUID assignedToUserId,
        @Pattern(regexp = "OPEN|IN_PROGRESS") String status
) {
}
