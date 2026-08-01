package com.translatelab.backend.plan.service;

import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PlanEntitlement;
import com.translatelab.backend.plan.entity.PlanEntitlementId;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.exception.FeatureNotAvailableException;
import com.translatelab.backend.plan.repository.PlanEntitlementRepository;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class EntitlementService {

    private static final String FREE_PLAN_CODE = "FREE";

    private final PlanEntitlementRepository planEntitlementRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final Clock clock;

    public EntitlementService(
            PlanEntitlementRepository planEntitlementRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            Clock clock
    ) {
        this.planEntitlementRepository = planEntitlementRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ResolvedEntitlement resolveEntitlement(
            UUID userId,
            FeatureCode featureCode
    ) {
        Objects.requireNonNull(
                featureCode,
                "Код функции не должен быть null"
        );

        String planCode = resolvePlanCode(userId);

        PlanEntitlementId entitlementId = new PlanEntitlementId(
                planCode,
                featureCode
        );

        PlanEntitlement entitlement = planEntitlementRepository
                .findByIdAndPlan_ActiveTrue(entitlementId)
                .orElseThrow(FeatureNotAvailableException::new);

        return toResolvedEntitlement(entitlement);
    }

    private String resolvePlanCode(UUID userId) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        Instant now = clock.instant();

        return userSubscriptionRepository
                .findEffectiveActiveByUserIdAt(userId, now)
                .map(subscription -> subscription.getPlan().getCode())
                .orElse(FREE_PLAN_CODE);
    }

    private ResolvedEntitlement toResolvedEntitlement(
            PlanEntitlement entitlement
    ) {
        SubscriptionPlan plan = entitlement.getPlan();

        return new ResolvedEntitlement(
                plan.getCode(),
                plan.getDisplayName(),
                entitlement.getId().getFeatureCode(),
                entitlement.getLimitUnits(),
                entitlement.getPeriodType(),
                entitlement.isUnlimited()
        );
    }
}