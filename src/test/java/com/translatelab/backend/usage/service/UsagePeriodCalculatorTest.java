package com.translatelab.backend.usage.service;

import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.usage.dto.UsagePeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsagePeriodCalculatorTest {

    private final UsagePeriodCalculator calculator =
            new UsagePeriodCalculator();

    @Test
    void shouldCalculateUtcMonthlyPeriod() {
        UsagePeriod period = calculator.calculate(
                PeriodType.MONTH,
                Instant.parse("2026-07-17T15:42:31.123Z")
        );

        assertPeriod(
                period,
                "2026-07-01T00:00:00Z",
                "2026-08-01T00:00:00Z"
        );
    }

    @Test
    void shouldIncludeExactStartOfMonth() {
        UsagePeriod period = calculator.calculate(
                PeriodType.MONTH,
                Instant.parse("2026-07-01T00:00:00Z")
        );

        assertPeriod(
                period,
                "2026-07-01T00:00:00Z",
                "2026-08-01T00:00:00Z"
        );
    }

    @Test
    void shouldCalculateLeapYearFebruary() {
        UsagePeriod period = calculator.calculate(
                PeriodType.MONTH,
                Instant.parse("2024-02-29T23:59:59.999999999Z")
        );

        assertPeriod(
                period,
                "2024-02-01T00:00:00Z",
                "2024-03-01T00:00:00Z"
        );
    }

    @Test
    void shouldCalculatePeriodAcrossYearBoundary() {
        UsagePeriod period = calculator.calculate(
                PeriodType.MONTH,
                Instant.parse("2026-12-31T23:59:59.999999999Z")
        );

        assertPeriod(
                period,
                "2026-12-01T00:00:00Z",
                "2027-01-01T00:00:00Z"
        );
    }

    @Test
    void shouldRejectMissingArguments() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> calculator.calculate(
                                null,
                                Instant.parse("2026-07-17T15:42:31Z")
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> calculator.calculate(
                                PeriodType.MONTH,
                                null
                        )
                )
        );
    }

    private void assertPeriod(
            UsagePeriod period,
            String expectedStart,
            String expectedEnd
    ) {
        assertAll(
                () -> assertEquals(
                        Instant.parse(expectedStart),
                        period.periodStart()
                ),
                () -> assertEquals(
                        Instant.parse(expectedEnd),
                        period.periodEnd()
                )
        );
    }
}
