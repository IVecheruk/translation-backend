package com.translatelab.backend.usage.service;

import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.usage.dto.UsagePeriod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public class UsagePeriodCalculator {

    public UsagePeriod calculate(PeriodType periodType, Instant referenceTime) {
        Objects.requireNonNull(periodType, "Тип перевода не должен быть null");
        Objects.requireNonNull(referenceTime, "Опорный момент времени не должен быть null");

        return switch (periodType) {
            case MONTH -> calculateMonth(referenceTime);
        };
    }

    private UsagePeriod calculateMonth(Instant referenceTime) {
        YearMonth currentMonth = YearMonth.from(referenceTime.atZone(ZoneOffset.UTC));

        Instant periodStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).
                toInstant();

        return new UsagePeriod(periodStart, periodEnd);
    }
}