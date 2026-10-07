package com.translatelab.backend.auth.dto;

import java.time.Instant;

/** Internal result: the refresh secret is sent only in an HttpOnly cookie. */
public record SessionTokens(LoginResponse response, String refreshToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "SessionTokens[credentials=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
