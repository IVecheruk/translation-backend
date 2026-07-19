package com.translatelab.backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        Instant createdAt
) {
}
