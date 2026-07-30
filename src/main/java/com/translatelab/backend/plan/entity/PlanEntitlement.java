package com.translatelab.backend.plan.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "plan_entitlements")
public class PlanEntitlement {

    @EmbeddedId
    private PlanEntitlementId id;

    @MapsId("planCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_code", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "limit_units")
    private Integer limitUnits;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 16)
    private PeriodType periodType;

    @Column(name = "unlimited", nullable = false)
    private boolean unlimited;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanEntitlement(){}

    private PlanEntitlement(
            SubscriptionPlan plan,
            FeatureCode featureCode,
            Integer limitUnits,
            PeriodType periodType,
            boolean unlimited
    ) {
        this.plan = validatePlan(plan);
        this.id = new PlanEntitlementId(this.plan.getCode(), validateFeatureCode(featureCode));
        this.periodType = validatePeriodType(periodType);
        this.unlimited = unlimited;

        if (unlimited) {
            this.limitUnits = null;
        } else {
            this.limitUnits = validateLimitUnits(limitUnits);
        }
    }

    public static PlanEntitlement limited(
            SubscriptionPlan plan,
            FeatureCode featureCode,
            int limitUnits,
            PeriodType periodType
    ) {
        return new PlanEntitlement(
                plan,
                featureCode,
                limitUnits,
                periodType,
                false
        );
    }

    public static PlanEntitlement unlimited(
            SubscriptionPlan plan,
            FeatureCode featureCode,
            PeriodType periodType
    ) {
        return new PlanEntitlement(
                plan,
                featureCode,
                null,
                periodType,
                true
        );
    }

    public void makeLimited(int limitUnits) {
        this.limitUnits = validateLimitUnits(limitUnits);
        this.unlimited = false;
    }

    public void makeUnlimited() {
        this.limitUnits = null;
        this.unlimited = true;
    }

    public PlanEntitlementId getId() {
        return id;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public Integer getLimitUnits() {
        return limitUnits;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static SubscriptionPlan validatePlan(SubscriptionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Тариф не должен быть null");
        }

        return plan;
    }

    private static FeatureCode validateFeatureCode(FeatureCode featureCode) {
        if (featureCode == null) {
            throw new IllegalArgumentException("Код функции не должен быть null");
        }

        return featureCode;
    }

    private static PeriodType validatePeriodType(PeriodType periodType) {
        if (periodType == null) {
            throw new IllegalArgumentException("Тип периода не должен быть null");
        }

        return periodType;
    }

    private static int validateLimitUnits(Integer limitUnits) {
        if (limitUnits == null || limitUnits <= 0) {
            throw new IllegalArgumentException("Лимит должен быть положительным числом");
        }

        return limitUnits;
    }
}