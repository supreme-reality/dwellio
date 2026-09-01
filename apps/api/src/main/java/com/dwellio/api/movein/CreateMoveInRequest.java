package com.dwellio.api.movein;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateMoveInRequest(
        @NotNull UUID tenantId,
        @NotNull UUID bedId,
        @NotNull LocalDate moveInDate,
        @Valid List<MoveInServiceSelectionRequest> serviceSelections
) {
}
