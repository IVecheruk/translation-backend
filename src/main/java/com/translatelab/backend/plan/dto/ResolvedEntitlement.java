package com.translatelab.backend.plan.dto;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;

public record ResolvedEntitlement(
        String planCode,
        String planDisplayName,
        FeatureCode featureCode,
        Integer limitUnits,
        PeriodType periodType,
        boolean unlimited
) {
    public ResolvedEntitlement {
        if (planCode == null || planCode.isBlank()) {
            throw new IllegalArgumentException("Код тарифа не должен быть пустым");
        }

        if (planDisplayName == null || planDisplayName.isBlank()) {
            throw new IllegalArgumentException("Название тарифа не должно быть пустым");
        }

        if (featureCode == null) {
            throw new IllegalArgumentException("Код функции не должен быть null");
        }

        if (periodType == null) {
            throw new IllegalArgumentException("Тип периода не должен быть null");
        }

        if (unlimited && limitUnits != null) {
            throw new IllegalArgumentException("Безлимитное право не должно содержать лимит");
        }

        if (!unlimited && (limitUnits == null || limitUnits <= 0)) {
            throw new IllegalArgumentException("Ограниченное право должно содержать положительный лимит");
        }
    }
}
