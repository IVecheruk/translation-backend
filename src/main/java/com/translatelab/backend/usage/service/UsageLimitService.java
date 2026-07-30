package com.translatelab.backend.usage.service;

import com.translatelab.backend.config.UsageProperties;
import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.service.EntitlementService;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.usage.dto.UsagePeriod;
import com.translatelab.backend.usage.entity.FeatureUsageRecord;
import com.translatelab.backend.usage.exception.UsageLimitExceededException;
import com.translatelab.backend.usage.exception.UsageReservationNotFoundException;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UsageLimitService {

    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final FeatureUsageRecordRepository usageRecordRepository;
    private final UsagePeriodCalculator usagePeriodCalculator;
    private final UsageProperties usageProperties;
    private final Clock clock;
    private final TranslationJobRepository translationJobRepository;

    public UsageLimitService(
            UserRepository userRepository,
            EntitlementService entitlementService,
            FeatureUsageRecordRepository usageRecordRepository,
            UsagePeriodCalculator usagePeriodCalculator,
            UsageProperties usageProperties,
            Clock clock,
            TranslationJobRepository translationJobRepository
    ) {
        this.userRepository = userRepository;
        this.entitlementService = entitlementService;
        this.usageRecordRepository = usageRecordRepository;
        this.usagePeriodCalculator = usagePeriodCalculator;
        this.usageProperties = usageProperties;
        this.clock = clock;
        this.translationJobRepository = translationJobRepository;
    }

    @Transactional
    public UUID reserve(
            UUID userId,
            FeatureCode featureCode,
            int units
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );
        Objects.requireNonNull(
                featureCode,
                "Код функции не должен быть null"
        );

        if (units <= 0) {
            throw new IllegalArgumentException(
                    "Количество единиц должно быть положительным"
            );
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);

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

        if (!entitlement.unlimited()) {
            checkAvailableLimit(
                    userId,
                    featureCode,
                    units,
                    entitlement,
                    period
            );
        }

        Instant expiresAt = now.plus(
                usageProperties.reservationTtl()
        );

        FeatureUsageRecord reservation =
                FeatureUsageRecord.reserve(
                        user,
                        featureCode,
                        units,
                        period.periodStart(),
                        period.periodEnd(),
                        expiresAt
                );

        FeatureUsageRecord savedReservation =
                usageRecordRepository.save(reservation);

        return savedReservation.getId();
    }

    @Transactional
    public void consume(
            UUID reservationId,
            UUID translationJobId
    ) {
        Objects.requireNonNull(
                reservationId,
                "Идентификатор резервации не должен быть null"
        );

        Objects.requireNonNull(
                translationJobId,
                "Идентификатор задания перевода не должен быть null"
        );

        FeatureUsageRecord reservation =
                usageRecordRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(
                                UsageReservationNotFoundException::new
                        );

        UUID userId = reservation.getUser().getId();

        TranslationJob translationJob =
                translationJobRepository.findByIdAndUser_Id(
                        translationJobId,
                        userId
                ).orElseThrow(TranslationJobNotFoundException::new);

        reservation.consume(translationJob);
    }

    @Transactional
    public void release(UUID reservationId) {
        Objects.requireNonNull(
                reservationId,
                "Идентификатор резервации не должен быть null"
        );

        FeatureUsageRecord reservation =
                usageRecordRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(
                                UsageReservationNotFoundException::new
                        );

        reservation.release();
    }

    @Transactional
    public int releaseExpiredReservations(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Размер пакета должен быть положительным"
            );
        }

        Instant now = clock.instant();

        List<FeatureUsageRecord> expiredReservations =
                usageRecordRepository.findExpiredReservationsForUpdate(
                        now,
                        PageRequest.of(0, batchSize)
                );

        expiredReservations.forEach(FeatureUsageRecord::release);

        return expiredReservations.size();
    }

    private void checkAvailableLimit(
            UUID userId,
            FeatureCode featureCode,
            int requestedUnits,
            ResolvedEntitlement entitlement,
            UsagePeriod period
    ) {
        long occupiedUnits = usageRecordRepository.sumOccupiedUnits(
                userId,
                featureCode,
                period.periodStart(),
                period.periodEnd()
        );

        long resultingUnits = occupiedUnits + (long) requestedUnits;

        if (resultingUnits > entitlement.limitUnits()) {
            throw new UsageLimitExceededException();
        }
    }
}