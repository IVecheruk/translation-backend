package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtPropertiesSecurityTest {

    @Test
    void shouldAcceptPositiveTtlUpToOneHour() {
        assertDoesNotThrow(() -> properties(Duration.ofSeconds(1)));
        assertDoesNotThrow(() -> properties(Duration.ofHours(1)));
    }

    @Test
    void shouldRejectNonPositiveAndExcessiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> properties(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> properties(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> properties(Duration.ofHours(1).plusSeconds(1)));
    }

    private JwtProperties properties(Duration ttl) {
        return new JwtProperties(
                "secret",
                ttl,
                "https://api.translatelab.local",
                "translation-frontend",
                "primary"
        );
    }
}
