package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenPropertiesTest {
    @Test
    void validatesSessionDurationBounds() {
        for (Duration invalid : new Duration[]{Duration.ZERO, Duration.ofMillis(999),
                Duration.ofSeconds(-1), Duration.ofDays(30).plusSeconds(1)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new RefreshTokenProperties(invalid, true, "Lax"));
        }
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenProperties(null, true, "Lax"));
        assertDoesNotThrow(() -> new RefreshTokenProperties(Duration.ofSeconds(1), true, "Strict"));
        assertDoesNotThrow(() -> new RefreshTokenProperties(Duration.ofDays(30), true, "Lax"));
    }

    @Test
    void requiresSecureCookieForCrossSiteUse() {
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshTokenProperties(Duration.ofDays(7), false, "None"));
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshTokenProperties(Duration.ofDays(7), true, "invalid"));
        assertDoesNotThrow(() -> new RefreshTokenProperties(Duration.ofDays(7), true, "None"));
        assertDoesNotThrow(() -> new RefreshTokenProperties(Duration.ofDays(7), false, "Lax"));
    }
}
