package com.dwellio.api.notice;

import java.time.Instant;
import java.util.UUID;

public record NoticeResponse(
        UUID id,
        UUID propertyId,
        String title,
        String body,
        String status,
        Instant publishedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
