package com.translatelab.backend.auth.security;

import com.translatelab.backend.common.exception.RequestRateLimitExceededException;
import com.translatelab.backend.config.ApiRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptLimiterTest {

    @Test
    void shouldNormalizeAccountAndCombineItWithConnectionAddress() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                new ApiRateLimitProperties(10, 1, 10, 10, 10, 10, 100),
                Clock.fixed(
                        Instant.parse("2026-08-10T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        limiter.check(" User@Example.COM ", "192.0.2.1");

        assertThrows(
                RequestRateLimitExceededException.class,
                () -> limiter.check("user@example.com", "192.0.2.1")
        );
        assertDoesNotThrow(
                () -> limiter.check("user@example.com", "192.0.2.2")
        );
        assertDoesNotThrow(
                () -> limiter.check("other@example.com", "192.0.2.1")
        );
    }
}
