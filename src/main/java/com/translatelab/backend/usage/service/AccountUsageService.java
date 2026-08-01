package com.translatelab.backend.usage.service;

import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.service.EntitlementService;
import com.translatelab.backend.usage.dto.AccountUsageResponse;
import com.translatelab.backend.usage.dto.UsagePeriod;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountUsageService {

    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final UsagePeriodCalculator usagePeriodCalculator;
    private final FeatureUsageRecordRepository usageRecordRepository;
    private final Clock clock;

    public AccountUsageService(
            UserRepository userRepository,
            EntitlementService entitlementService,
            UsagePeriodCalculator usagePeriodCalculator,
            FeatureUsageRecordRepository usageRecordRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.entitlementService = entitlementService;
        this.usagePeriodCalculator = usagePeriodCalculator;
        this.usageRecordRepository = usageRecordRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountUsageResponse getCurrentUsage(UUID userId) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        FeatureCode featureCode =
                FeatureCode.DOCUMENT_TRANSLATION;

        ResolvedEntitlement entitlement =
                entitlementService.resolveEntitlement(
                        userId,
                        featureCode
                );

        Instant now = clock.instant();

        UsagePeriod period = usagePeriodCalculator.calculate(
                entitlement.periodType(),
                now
        );

        long usedUnits =
                usageRecordRepository.sumOccupiedUnits(
                        userId,
                        featureCode,
                        period.periodStart(),
                        period.periodEnd()
                );

        Long remainingUnits = entitlement.unlimited()
                ? null
                : Math.max(
                0L,
                entitlement.limitUnits().longValue()
                        - usedUnits
        );

        return new AccountUsageResponse(
                entitlement.planCode(),
                entitlement.planDisplayName(),
                entitlement.featureCode(),
                entitlement.periodType(),
                entitlement.unlimited(),
                entitlement.limitUnits(),
                usedUnits,
                remainingUnits,
                period.periodEnd()
        );
    }
}