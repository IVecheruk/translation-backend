package com.translatelab.backend.usage.dto;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountUsageResponseTest {

    private static final Instant RESETS_AT =
            Instant.parse("2026-09-01T00:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldCreateLimitedResponseEvenWhenUsedExceedsLimit() {
        AccountUsageResponse response = limitedResponse(
                10,
                0L
        );

        assertAll(
                () -> assertEquals("FREE", response.planCode()),
                () -> assertEquals(5, response.limitUnits()),
                () -> assertEquals(10, response.usedUnits()),
                () -> assertEquals(0L, response.remainingUnits()),
                () -> assertEquals(RESETS_AT, response.resetsAt())
        );
    }

    @Test
    void shouldCreateUnlimitedResponseWithoutLimitOrRemainingUnits() {
        AccountUsageResponse response = new AccountUsageResponse(
                "PRO",
                "Профессиональный",
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH,
                true,
                null,
                42,
                null,
                RESETS_AT
        );

        assertAll(
                () -> assertTrue(response.unlimited()),
                () -> assertNull(response.limitUnits()),
                () -> assertEquals(42, response.usedUnits()),
                () -> assertNull(response.remainingUnits())
        );
    }

    @Test
    void shouldSerializeAccordingToApiContract() throws Exception {
        AccountUsageResponse response = limitedResponse(2, 3L);

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
                "{\"plan_code\":\"FREE\","
                        + "\"plan_display_name\":\"Бесплатный\","
                        + "\"feature_code\":\"DOCUMENT_TRANSLATION\","
                        + "\"period_type\":\"MONTH\","
                        + "\"unlimited\":false,"
                        + "\"limit_units\":5,"
                        + "\"used_units\":2,"
                        + "\"remaining_units\":3,"
                        + "\"resets_at\":"
                        + "\"2026-09-01T00:00:00Z\"}",
                json
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectMissingPlanCode(String planCode) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountUsageResponse(
                        planCode,
                        "Бесплатный",
                        FeatureCode.DOCUMENT_TRANSLATION,
                        PeriodType.MONTH,
                        false,
                        5,
                        2,
                        3L,
                        RESETS_AT
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectMissingPlanDisplayName(String planDisplayName) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountUsageResponse(
                        "FREE",
                        planDisplayName,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        PeriodType.MONTH,
                        false,
                        5,
                        2,
                        3L,
                        RESETS_AT
                )
        );
    }

    @Test
    void shouldRejectMissingRequiredValuesAndNegativeUsage() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountUsageResponse(
                                "FREE",
                                "Бесплатный",
                                null,
                                PeriodType.MONTH,
                                false,
                                5,
                                2,
                                3L,
                                RESETS_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountUsageResponse(
                                "FREE",
                                "Бесплатный",
                                FeatureCode.DOCUMENT_TRANSLATION,
                                null,
                                false,
                                5,
                                2,
                                3L,
                                RESETS_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountUsageResponse(
                                "FREE",
                                "Бесплатный",
                                FeatureCode.DOCUMENT_TRANSLATION,
                                PeriodType.MONTH,
                                false,
                                5,
                                -1,
                                3L,
                                RESETS_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountUsageResponse(
                                "FREE",
                                "Бесплатный",
                                FeatureCode.DOCUMENT_TRANSLATION,
                                PeriodType.MONTH,
                                false,
                                5,
                                2,
                                3L,
                                null
                        )
                )
        );
    }

    @Test
    void shouldRejectLimitOrRemainingUnitsForUnlimitedResponse() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountUsageResponse(
                                "PRO",
                                "Профессиональный",
                                FeatureCode.DOCUMENT_TRANSLATION,
                                PeriodType.MONTH,
                                true,
                                100,
                                2,
                                null,
                                RESETS_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountUsageResponse(
                                "PRO",
                                "Профессиональный",
                                FeatureCode.DOCUMENT_TRANSLATION,
                                PeriodType.MONTH,
                                true,
                                null,
                                2,
                                98L,
                                RESETS_AT
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidLimitedResponse() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> limitedResponse(null, 0, 0L)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> limitedResponse(0, 0, 0L)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> limitedResponse(5, 2, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> limitedResponse(5, 2, -1L)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> limitedResponse(5, 0, 6L)
                )
        );
    }

    private AccountUsageResponse limitedResponse(
            long usedUnits,
            Long remainingUnits
    ) {
        return limitedResponse(5, usedUnits, remainingUnits);
    }

    private AccountUsageResponse limitedResponse(
            Integer limitUnits,
            long usedUnits,
            Long remainingUnits
    ) {
        return new AccountUsageResponse(
                "FREE",
                "Бесплатный",
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH,
                false,
                limitUnits,
                usedUnits,
                remainingUnits,
                RESETS_AT
        );
    }
}
