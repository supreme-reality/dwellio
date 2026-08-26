package com.dwellio.api.user;

import java.util.UUID;

public record MeResponse(UUID id, String email, String name, String status) {
}
