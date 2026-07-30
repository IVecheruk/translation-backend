package com.translatelab.backend.usage.dto;

import java.time.Instant;

public record UsagePeriod(
        Instant periodStart,
        Instant periodEnd
) {

    public UsagePeriod {
        if (periodStart == null) {
            throw new IllegalArgumentException("Начало периода не должно быть null");
        }

        if (periodEnd == null) {
            throw new IllegalArgumentException("Конец периода не должен быть null");
        }

        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("Конец периода должен быть позже его начала");
        }
    }
}