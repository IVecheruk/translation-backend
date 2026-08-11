package com.translatelab.backend.usage.service;

import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.plan.service.EntitlementService;
import com.translatelab.backend.usage.dto.AccountUsageResponse;
import com.translatelab.backend.usage.dto.UsagePeriod;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountUsageServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "bbffb2d2-dc8b-481a-94b5-aa386f6c5aad"
    );
    private static final FeatureCode FEATURE_CODE =
            FeatureCode.DOCUMENT_TRANSLATION;
    private static final Instant NOW =
            Instant.parse("2026-08-17T14:30:00Z");
    private static final UsagePeriod PERIOD = new UsagePeriod(
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z")
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private UsagePeriodCalculator usagePeriodCalculator;

    @Mock
    private FeatureUsageRecordRepository usageRecordRepository;

    private AccountUsageService service;

    @BeforeEach
    void setUp() {
        service = new AccountUsageService(
                userRepository,
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReturnCurrentLimitedUsage() {
        ResolvedEntitlement entitlement = limitedEntitlement();
        givenCommonFlow(entitlement);
        given(usageRecordRepository.sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        )).willReturn(2L);

        AccountUsageResponse response =
                service.getCurrentUsage(USER_ID);

        assertAll(
                () -> assertEquals("FREE", response.planCode()),
                () -> assertEquals(
                        "Бесплатный",
                        response.planDisplayName()
                ),
                () -> assertEquals(
                        FEATURE_CODE,
                        response.featureCode()
                ),
                () -> assertEquals(
                        PeriodType.MONTH,
                        response.periodType()
                ),
                () -> assertFalse(response.unlimited()),
                () -> assertEquals(5, response.limitUnits()),
                () -> assertEquals(2, response.usedUnits()),
                () -> assertEquals(3L, response.remainingUnits()),
                () -> assertEquals(
                        PERIOD.periodEnd(),
                        response.resetsAt()
                )
        );

        InOrder order = inOrder(
                userRepository,
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository
        );
        order.verify(userRepository).existsById(USER_ID);
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
    }

    @Test
    void shouldClampRemainingUnitsToZeroWhenUsageExceedsLimit() {
        ResolvedEntitlement entitlement = limitedEntitlement();
        givenCommonFlow(entitlement);
        given(usageRecordRepository.sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        )).willReturn(7L);

        AccountUsageResponse response =
                service.getCurrentUsage(USER_ID);

        assertAll(
                () -> assertEquals(7, response.usedUnits()),
                () -> assertEquals(0L, response.remainingUnits())
        );
    }

    @Test
    void shouldReturnUnlimitedUsageWithoutLimitOrRemainingUnits() {
        ResolvedEntitlement entitlement = new ResolvedEntitlement(
                "PRO",
                "Профессиональный",
                FEATURE_CODE,
                null,
                PeriodType.MONTH,
                true
        );
        givenCommonFlow(entitlement);
        given(usageRecordRepository.sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        )).willReturn(42L);

        AccountUsageResponse response =
                service.getCurrentUsage(USER_ID);

        assertAll(
                () -> assertTrue(response.unlimited()),
                () -> assertNull(response.limitUnits()),
                () -> assertEquals(42, response.usedUnits()),
                () -> assertNull(response.remainingUnits()),
                () -> assertEquals(
                        PERIOD.periodEnd(),
                        response.resetsAt()
                )
        );
        verify(usageRecordRepository).sumOccupiedUnits(
                USER_ID,
                FEATURE_CODE,
                PERIOD.periodStart(),
                PERIOD.periodEnd()
        );
    }

    @Test
    void shouldRejectMissingUserBeforeResolvingEntitlement() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThrows(
                UserNotFoundException.class,
                () -> service.getCurrentUsage(USER_ID)
        );

        verify(userRepository).existsById(USER_ID);
        verifyNoInteractions(
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository
        );
    }

    @Test
    void shouldRejectNullUserIdBeforeCallingDependencies() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.getCurrentUsage(null)
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(
                userRepository,
                entitlementService,
                usagePeriodCalculator,
                usageRecordRepository
        );
    }

    private void givenCommonFlow(
            ResolvedEntitlement entitlement
    ) {
        given(userRepository.existsById(USER_ID)).willReturn(true);
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

    private ResolvedEntitlement limitedEntitlement() {
        return new ResolvedEntitlement(
                "FREE",
                "Бесплатный",
                FEATURE_CODE,
                5,
                PeriodType.MONTH,
                false
        );
    }
}
