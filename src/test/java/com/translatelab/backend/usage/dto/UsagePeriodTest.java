package com.translatelab.backend.usage.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsagePeriodTest {

    private static final Instant PERIOD_START =
            Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant PERIOD_END =
            Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void shouldCreateValidUsagePeriod() {
        UsagePeriod period = new UsagePeriod(
                PERIOD_START,
                PERIOD_END
        );

        assertAll(
                () -> assertEquals(
                        PERIOD_START,
                        period.periodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        period.periodEnd()
                )
        );
    }

    @Test
    void shouldRejectMissingPeriodBoundaries() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new UsagePeriod(null, PERIOD_END)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new UsagePeriod(PERIOD_START, null)
                )
        );
    }

    @Test
    void shouldRejectNonIncreasingPeriod() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new UsagePeriod(
                                PERIOD_START,
                                PERIOD_START
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new UsagePeriod(
                                PERIOD_START,
                                PERIOD_START.minusSeconds(1)
                        )
                )
        );
    }
}
