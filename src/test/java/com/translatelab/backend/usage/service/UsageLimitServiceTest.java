package com.translatelab.backend.usage.service;

import com.translatelab.backend.config.UsageProperties;
import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.plan.service.EntitlementService;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.usage.dto.UsagePeriod;
import com.translatelab.backend.usage.entity.FeatureUsageRecord;
import com.translatelab.backend.usage.entity.UsageStatus;
import com.translatelab.backend.usage.exception.UsageLimitExceededException;
import com.translatelab.backend.usage.exception.UsageReservationNotFoundException;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UsageLimitServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "90fd389b-a3b2-4ed4-9843-1bf4e438894d"
    );
    private static final UUID RESERVATION_ID = UUID.fromString(
            "43574140-763c-42fc-8cf2-c8f01482c33c"
    );
    private static final UUID TRANSLATION_JOB_ID = UUID.fromString(
            "f24971f7-6554-4cf8-aa66-b4ad98c7044d"
    );
    private static final FeatureCode FEATURE_CODE =
            FeatureCode.DOCUMENT_TRANSLATION;
    private static final Instant NOW =
            Instant.parse("2026-07-17T15:42:31Z");
    private static final UsagePeriod PERIOD = new UsagePeriod(
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z")
    );
    private static final Duration RESERVATION_TTL =
            Duration.ofMinutes(15);

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private FeatureUsageRecordRepository usageRecordRepository;

    @Mock
    private UsagePeriodCalculator usagePeriodCalculator;

    @Mock
    private TranslationJobRepository translationJobRepository;

    @Mock
    private User user;

    @Mock
    private FeatureUsageRecord savedReservation;

    private UsageLimitService service;

    @BeforeEach
    void setUp() {
        service = new UsageLimitService(
                userRepository,
                entitlementService,
                usageRecordRepository,
                usagePeriodCalculator,
                new UsageProperties(
                        RESERVATION_TTL,
                        Duration.ofMinutes(1),
                        100
                ),
                Clock.fixed(NOW, ZoneOffset.UTC),
                translationJobRepository,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
    }

    @Test
    void shouldReserveUnitsUpToExactLimitedQuota() {
        ResolvedEntitlement entitlement = limitedEntitlement(5);
        givenCommonReservationFlow(entitlement);
        given(usageRecordRepository.sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        )).willReturn(3L);
        given(usageRecordRepository.save(any(
                FeatureUsageRecord.class
        ))).willReturn(savedReservation);
        given(savedReservation.getId()).willReturn(RESERVATION_ID);

        UUID result = service.reserve(USER_ID, FEATURE_CODE, 2);

        ArgumentCaptor<FeatureUsageRecord> reservationCaptor =
                ArgumentCaptor.forClass(FeatureUsageRecord.class);
        InOrder order = inOrder(
                userRepository,
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository
        );
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(entitlementService).resolveEntitlement(
                USER_ID,
                FEATURE_CODE,
                NOW
        );
        order.verify(usagePeriodCalculator).calculate(
                PeriodType.MONTH,
                NOW
        );
        order.verify(usageRecordRepository).sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        );
        order.verify(usageRecordRepository).save(
                reservationCaptor.capture()
        );

        FeatureUsageRecord reservation = reservationCaptor.getValue();
        assertAll(
                () -> assertEquals(RESERVATION_ID, result),
                () -> assertSame(user, reservation.getUser()),
                () -> assertEquals(
                        FEATURE_CODE,
                        reservation.getFeatureCode()
                ),
                () -> assertEquals(2, reservation.getUnits()),
                () -> assertEquals(
                        PERIOD.periodStart(),
                        reservation.getPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD.periodEnd(),
                        reservation.getPeriodEnd()
                ),
                () -> assertEquals(
                        UsageStatus.RESERVED,
                        reservation.getStatus()
                ),
                () -> assertEquals(
                        NOW.plus(RESERVATION_TTL),
                        reservation.getExpiresAt()
                )
        );
    }

    @Test
    void shouldRejectReservationExceedingLimitedQuota() {
        ResolvedEntitlement entitlement = limitedEntitlement(5);
        givenCommonReservationFlow(entitlement);
        given(usageRecordRepository.sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        )).willReturn(4L);

        assertThrows(
                UsageLimitExceededException.class,
                () -> service.reserve(USER_ID, FEATURE_CODE, 2)
        );

        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldReserveUnlimitedUsageWithoutCountingOccupiedUnits() {
        ResolvedEntitlement entitlement = unlimitedEntitlement();
        givenCommonReservationFlow(entitlement);
        given(usageRecordRepository.save(any(
                FeatureUsageRecord.class
        ))).willReturn(savedReservation);
        given(savedReservation.getId()).willReturn(RESERVATION_ID);

        UUID result = service.reserve(USER_ID, FEATURE_CODE, 1);

        assertEquals(RESERVATION_ID, result);
        verify(usageRecordRepository, never()).sumOccupiedUnits(
                any(),
                any(),
                any(),
                any()
        );
        verify(usageRecordRepository).save(any(
                FeatureUsageRecord.class
        ));
    }

    @Test
    void shouldRejectMissingUserBeforeResolvingEntitlement() {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> service.reserve(USER_ID, FEATURE_CODE, 1)
        );

        verifyNoInteractions(
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository,
                translationJobRepository
        );
    }

    @Test
    void shouldRejectInvalidArgumentsBeforeCallingDependencies() {
        NullPointerException missingUserId = assertThrows(
                NullPointerException.class,
                () -> service.reserve(null, FEATURE_CODE, 1)
        );
        NullPointerException missingFeatureCode = assertThrows(
                NullPointerException.class,
                () -> service.reserve(USER_ID, null, 1)
        );
        IllegalArgumentException zeroUnits = assertThrows(
                IllegalArgumentException.class,
                () -> service.reserve(USER_ID, FEATURE_CODE, 0)
        );
        IllegalArgumentException negativeUnits = assertThrows(
                IllegalArgumentException.class,
                () -> service.reserve(USER_ID, FEATURE_CODE, -1)
        );

        assertAll(
                () -> assertEquals(
                        "Идентификатор пользователя не должен быть null",
                        missingUserId.getMessage()
                ),
                () -> assertEquals(
                        "Код функции не должен быть null",
                        missingFeatureCode.getMessage()
                ),
                () -> assertEquals(
                        "Количество единиц должно быть положительным",
                        zeroUnits.getMessage()
                ),
                () -> assertEquals(
                        "Количество единиц должно быть положительным",
                        negativeUnits.getMessage()
                )
        );
        verifyNoInteractions(
                userRepository,
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository,
                translationJobRepository
        );
    }

    @Test
    void shouldConsumeReservationForOwnersTranslationJob() {
        given(user.getId()).willReturn(USER_ID);
        FeatureUsageRecord reservation = createReservation();
        TranslationJob translationJob = createTranslationJob();
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(reservation));
        given(translationJobRepository.findByIdAndUser_Id(
                TRANSLATION_JOB_ID,
                USER_ID
        )).willReturn(Optional.of(translationJob));

        service.consume(RESERVATION_ID, TRANSLATION_JOB_ID);

        InOrder order = inOrder(
                usageRecordRepository,
                translationJobRepository
        );
        order.verify(usageRecordRepository).findByIdForUpdate(
                RESERVATION_ID
        );
        order.verify(translationJobRepository).findByIdAndUser_Id(
                TRANSLATION_JOB_ID,
                USER_ID
        );
        assertAll(
                () -> assertEquals(
                        UsageStatus.CONSUMED,
                        reservation.getStatus()
                ),
                () -> assertSame(
                        translationJob,
                        reservation.getTranslationJob()
                ),
                () -> assertNull(reservation.getExpiresAt())
        );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectMissingReservationBeforeLoadingTranslationJob() {
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.empty());

        assertThrows(
                UsageReservationNotFoundException.class,
                () -> service.consume(
                        RESERVATION_ID,
                        TRANSLATION_JOB_ID
                )
        );

        verifyNoInteractions(translationJobRepository);
    }

    @Test
    void shouldRejectMissingOrForeignTranslationJob() {
        given(user.getId()).willReturn(USER_ID);
        FeatureUsageRecord reservation = createReservation();
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(reservation));
        given(translationJobRepository.findByIdAndUser_Id(
                TRANSLATION_JOB_ID,
                USER_ID
        )).willReturn(Optional.empty());

        assertThrows(
                TranslationJobNotFoundException.class,
                () -> service.consume(
                        RESERVATION_ID,
                        TRANSLATION_JOB_ID
                )
        );

        assertAll(
                () -> assertEquals(
                        UsageStatus.RESERVED,
                        reservation.getStatus()
                ),
                () -> assertEquals(
                        NOW.plus(RESERVATION_TTL),
                        reservation.getExpiresAt()
                ),
                () -> assertNull(reservation.getTranslationJob())
        );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectRepeatedConsumption() {
        given(user.getId()).willReturn(USER_ID);
        FeatureUsageRecord reservation = createReservation();
        TranslationJob translationJob = createTranslationJob();
        reservation.consume(translationJob);
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(reservation));
        given(translationJobRepository.findByIdAndUser_Id(
                TRANSLATION_JOB_ID,
                USER_ID
        )).willReturn(Optional.of(translationJob));

        assertThrows(
                IllegalStateException.class,
                () -> service.consume(
                        RESERVATION_ID,
                        TRANSLATION_JOB_ID
                )
        );

        assertAll(
                () -> assertEquals(
                        UsageStatus.CONSUMED,
                        reservation.getStatus()
                ),
                () -> assertSame(
                        translationJob,
                        reservation.getTranslationJob()
                ),
                () -> assertNull(reservation.getExpiresAt())
        );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectMissingConsumeArgumentsBeforeCallingDependencies() {
        NullPointerException missingReservationId = assertThrows(
                NullPointerException.class,
                () -> service.consume(null, TRANSLATION_JOB_ID)
        );
        NullPointerException missingTranslationJobId = assertThrows(
                NullPointerException.class,
                () -> service.consume(RESERVATION_ID, null)
        );

        assertAll(
                () -> assertEquals(
                        "Идентификатор резервации не должен быть null",
                        missingReservationId.getMessage()
                ),
                () -> assertEquals(
                        "Идентификатор задания перевода не должен быть null",
                        missingTranslationJobId.getMessage()
                )
        );
        verifyNoInteractions(
                usageRecordRepository,
                translationJobRepository
        );
    }

    @Test
    void shouldReleaseActiveReservation() {
        FeatureUsageRecord reservation = createReservation();
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(reservation));

        service.release(RESERVATION_ID);

        assertAll(
                () -> assertEquals(
                        UsageStatus.RELEASED,
                        reservation.getStatus()
                ),
                () -> assertNull(reservation.getExpiresAt()),
                () -> assertNull(reservation.getTranslationJob())
        );
        verify(usageRecordRepository).findByIdForUpdate(
                RESERVATION_ID
        );
        verify(usageRecordRepository, never()).save(any());
        verifyNoInteractions(translationJobRepository);
    }

    @Test
    void shouldRejectReleaseOfMissingReservation() {
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.empty());

        assertThrows(
                UsageReservationNotFoundException.class,
                () -> service.release(RESERVATION_ID)
        );

        verify(usageRecordRepository, never()).save(any());
        verifyNoInteractions(translationJobRepository);
    }

    @Test
    void shouldRejectRepeatedRelease() {
        FeatureUsageRecord reservation = createReservation();
        reservation.release();
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(reservation));

        assertThrows(
                IllegalStateException.class,
                () -> service.release(RESERVATION_ID)
        );

        assertAll(
                () -> assertEquals(
                        UsageStatus.RELEASED,
                        reservation.getStatus()
                ),
                () -> assertNull(reservation.getExpiresAt()),
                () -> assertNull(reservation.getTranslationJob())
        );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectReleaseOfConsumedReservation() {
        FeatureUsageRecord reservation = createReservation();
        TranslationJob translationJob = createTranslationJob();
        reservation.consume(translationJob);
        given(usageRecordRepository.findByIdForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(reservation));

        assertThrows(
                IllegalStateException.class,
                () -> service.release(RESERVATION_ID)
        );

        assertAll(
                () -> assertEquals(
                        UsageStatus.CONSUMED,
                        reservation.getStatus()
                ),
                () -> assertSame(
                        translationJob,
                        reservation.getTranslationJob()
                ),
                () -> assertNull(reservation.getExpiresAt())
        );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectMissingReleaseIdBeforeCallingDependencies() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.release(null)
        );

        assertEquals(
                "Идентификатор резервации не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(
                usageRecordRepository,
                translationJobRepository
        );
    }

    @Test
    void shouldReleaseExpiredReservationsInRequestedBatch() {
        FeatureUsageRecord firstReservation = createReservation();
        FeatureUsageRecord secondReservation = createReservation();
        given(usageRecordRepository.findExpiredReservationsForUpdate(
                NOW,
                PageRequest.of(0, 2)
        )).willReturn(List.of(firstReservation, secondReservation));

        int releasedCount = service.releaseExpiredReservations(2);

        assertAll(
                () -> assertEquals(2, releasedCount),
                () -> assertEquals(
                        UsageStatus.RELEASED,
                        firstReservation.getStatus()
                ),
                () -> assertNull(firstReservation.getExpiresAt()),
                () -> assertEquals(
                        UsageStatus.RELEASED,
                        secondReservation.getStatus()
                ),
                () -> assertNull(secondReservation.getExpiresAt())
        );
        verify(usageRecordRepository)
                .findExpiredReservationsForUpdate(
                        NOW,
                        PageRequest.of(0, 2)
                );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldReturnZeroWhenNoExpiredReservationsExist() {
        given(usageRecordRepository.findExpiredReservationsForUpdate(
                NOW,
                PageRequest.of(0, 10)
        )).willReturn(List.of());

        int releasedCount = service.releaseExpiredReservations(10);

        assertEquals(0, releasedCount);
        verify(usageRecordRepository)
                .findExpiredReservationsForUpdate(
                        NOW,
                        PageRequest.of(0, 10)
                );
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidExpiredReservationBatchSize() {
        IllegalArgumentException zeroBatchSize = assertThrows(
                IllegalArgumentException.class,
                () -> service.releaseExpiredReservations(0)
        );
        IllegalArgumentException negativeBatchSize = assertThrows(
                IllegalArgumentException.class,
                () -> service.releaseExpiredReservations(-1)
        );

        assertAll(
                () -> assertEquals(
                        "Размер пакета должен быть положительным",
                        zeroBatchSize.getMessage()
                ),
                () -> assertEquals(
                        "Размер пакета должен быть положительным",
                        negativeBatchSize.getMessage()
                )
        );
        verifyNoInteractions(usageRecordRepository);
    }

    private void givenCommonReservationFlow(
            ResolvedEntitlement entitlement
    ) {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(entitlementService.resolveEntitlement(
                USER_ID,
                FEATURE_CODE,
                NOW
        )).willReturn(entitlement);
        given(usagePeriodCalculator.calculate(
                PeriodType.MONTH,
                NOW
        )).willReturn(PERIOD);
    }

    private ResolvedEntitlement limitedEntitlement(int limitUnits) {
        return new ResolvedEntitlement(
                "FREE",
                "Бесплатный",
                FEATURE_CODE,
                limitUnits,
                PeriodType.MONTH,
                false
        );
    }

    private ResolvedEntitlement unlimitedEntitlement() {
        return new ResolvedEntitlement(
                "PRO",
                "Профессиональный",
                FEATURE_CODE,
                null,
                PeriodType.MONTH,
                true
        );
    }

    private FeatureUsageRecord createReservation() {
        return FeatureUsageRecord.reserve(
                user,
                FEATURE_CODE,
                1,
                PERIOD.periodStart(),
                PERIOD.periodEnd(),
                NOW.plus(RESERVATION_TTL)
        );
    }

    private TranslationJob createTranslationJob() {
        return new TranslationJob(
                user,
                "uploads/source.docx",
                "results/result.docx",
                "ru",
                "en",
                FileFormat.DOCX
        );
    }
}
