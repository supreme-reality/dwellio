package com.dwellio.api.movein;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateMoveInRequest(
        UUID bedId,
        LocalDate moveInDate,
        @Valid List<MoveInServiceSelectionRequest> serviceSelections
) {
}
