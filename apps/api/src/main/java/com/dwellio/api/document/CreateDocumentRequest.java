package com.dwellio.api.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 120) String contentType,
        UUID tenantId,
        UUID tenancyId
) {
}
