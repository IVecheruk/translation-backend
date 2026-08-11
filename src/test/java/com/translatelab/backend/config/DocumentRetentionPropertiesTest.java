package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentRetentionPropertiesTest {

    @Test
    void shouldKeepValidRetentionConfiguration() {
        DocumentRetentionProperties properties = properties(
                Duration.ofDays(1),
                100
        );

        assertEquals(Duration.ofDays(1), properties.completedSourceRetention());
        assertEquals(100, properties.cleanupBatchSize());
    }

    @Test
    void shouldRejectNonPositiveDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ZERO, 100)
        );
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofDays(1), 0)
        );
    }

    @Test
    void shouldRejectExcessiveBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofDays(1), 1001)
        );
    }

    private DocumentRetentionProperties properties(
            Duration sourceRetention,
            int batchSize
    ) {
        return new DocumentRetentionProperties(
                sourceRetention,
                Duration.ofDays(1),
                Duration.ofDays(30),
                Duration.ofDays(1),
                Duration.ofDays(1),
                Duration.ofMinutes(10),
                batchSize
        );
    }
}
