package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AvatarPropertiesTest {

    @Test
    void shouldRejectNonPositiveSizesAndDurations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(DataSize.ofBytes(0), Duration.ofHours(1))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(DataSize.ofMegabytes(2), Duration.ZERO)
        );
    }

    private AvatarProperties properties(
            DataSize uploadSize,
            Duration retention
    ) {
        return new AvatarProperties(
                uploadSize,
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(64),
                4096,
                4096,
                16_777_216,
                retention,
                Duration.ofHours(1),
                100
        );
    }
}
