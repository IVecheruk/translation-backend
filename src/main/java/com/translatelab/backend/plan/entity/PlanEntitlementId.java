package com.translatelab.backend.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class PlanEntitlementId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Pattern PLAN_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_code", nullable = false, length = 64)
    private FeatureCode featureCode;

    protected PlanEntitlementId(){}

    public PlanEntitlementId(String planCode, FeatureCode featureCode) {
        this.planCode = validatePlanCode(planCode);
        this.featureCode = validateFeatureCode(featureCode);
    }

    public String getPlanCode() {
        return planCode;
    }

    public FeatureCode getFeatureCode() {
        return featureCode;
    }

    private static String validatePlanCode(String planCode) {
        if (planCode == null) {
            throw new IllegalArgumentException("Код тарифа не должен быть null");
        }

        if (!PLAN_CODE_PATTERN.matcher(planCode).matches()) {
            throw new IllegalArgumentException("Некорректный формат кода тарифа");
        }

        return planCode;
    }

    private static FeatureCode validateFeatureCode(FeatureCode featureCode) {
        if (featureCode == null) {
            throw new IllegalArgumentException("Код функции не должен быть null");
        }

        return featureCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof PlanEntitlementId that)) {
            return false;
        }

        return Objects.equals(planCode, that.planCode) && featureCode == that.featureCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(planCode, featureCode);
    }
}