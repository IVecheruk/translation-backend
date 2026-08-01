package com.translatelab.backend.usage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;

import java.time.Instant;

public record AccountUsageResponse(

        @JsonProperty("plan_code")
        String planCode,

        @JsonProperty("plan_display_name")
        String planDisplayName,

        @JsonProperty("feature_code")
        FeatureCode featureCode,

        @JsonProperty("period_type")
        PeriodType periodType,

        boolean unlimited,

        @JsonProperty("limit_units")
        Integer limitUnits,

        @JsonProperty("used_units")
        long usedUnits,

        @JsonProperty("remaining_units")
        Long remainingUnits,

        @JsonProperty("resets_at")
        Instant resetsAt
) {

    public AccountUsageResponse {
        if (planCode == null || planCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Код тарифа не должен быть пустым"
            );
        }

        if (planDisplayName == null
                || planDisplayName.isBlank()) {
            throw new IllegalArgumentException(
                    "Название тарифа не должно быть пустым"
            );
        }

        if (featureCode == null) {
            throw new IllegalArgumentException(
                    "Код функции не должен быть null"
            );
        }

        if (periodType == null) {
            throw new IllegalArgumentException(
                    "Тип периода не должен быть null"
            );
        }

        if (usedUnits < 0) {
            throw new IllegalArgumentException(
                    "Количество использованных единиц "
                            + "не должно быть отрицательным"
            );
        }

        if (resetsAt == null) {
            throw new IllegalArgumentException(
                    "Время сброса лимита не должно быть null"
            );
        }

        if (unlimited) {
            if (limitUnits != null || remainingUnits != null) {
                throw new IllegalArgumentException(
                        "Безлимитное право не должно содержать "
                                + "лимит или остаток"
                );
            }
        } else {
            if (limitUnits == null || limitUnits <= 0) {
                throw new IllegalArgumentException(
                        "Ограниченное право должно содержать "
                                + "положительный лимит"
                );
            }

            if (remainingUnits == null || remainingUnits < 0) {
                throw new IllegalArgumentException(
                        "Остаток лимита не должен быть отрицательным "
                                + "или отсутствовать"
                );
            }

            if (remainingUnits > limitUnits.longValue()) {
                throw new IllegalArgumentException(
                        "Остаток лимита не должен превышать лимит"
                );
            }
        }
    }
}