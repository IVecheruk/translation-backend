package com.translatelab.backend.usage.entity;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureUsageRecordTest {

    private static final Instant PERIOD_START =
            Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant PERIOD_END =
            Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-07-30T12:15:00Z");

    @Test
    void shouldCreateReservedUsageRecord() {
        User user = createUser("user@example.com");

        FeatureUsageRecord record = reserve(user);

        assertAll(
                () -> assertNull(record.getId()),
                () -> assertSame(user, record.getUser()),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        record.getFeatureCode()
                ),
                () -> assertEquals(1, record.getUnits()),
                () -> assertEquals(PERIOD_START, record.getPeriodStart()),
                () -> assertEquals(PERIOD_END, record.getPeriodEnd()),
                () -> assertEquals(UsageStatus.RESERVED, record.getStatus()),
                () -> assertNull(record.getTranslationJob()),
                () -> assertEquals(EXPIRES_AT, record.getExpiresAt()),
                () -> assertNull(record.getCreatedAt()),
                () -> assertNull(record.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectMissingReservationArguments() {
        User user = createUser("user@example.com");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                null,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                1,
                                PERIOD_START,
                                PERIOD_END,
                                EXPIRES_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                user,
                                null,
                                1,
                                PERIOD_START,
                                PERIOD_END,
                                EXPIRES_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                user,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                1,
                                null,
                                PERIOD_END,
                                EXPIRES_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                user,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                1,
                                PERIOD_START,
                                null,
                                EXPIRES_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                user,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                1,
                                PERIOD_START,
                                PERIOD_END,
                                null
                        )
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldRejectNonPositiveUnits(int units) {
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureUsageRecord.reserve(
                        createUser("user@example.com"),
                        FeatureCode.DOCUMENT_TRANSLATION,
                        units,
                        PERIOD_START,
                        PERIOD_END,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectInvalidPeriod() {
        User user = createUser("user@example.com");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                user,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                1,
                                PERIOD_START,
                                PERIOD_START,
                                EXPIRES_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> FeatureUsageRecord.reserve(
                                user,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                1,
                                PERIOD_START,
                                PERIOD_START.minusSeconds(1),
                                EXPIRES_AT
                        )
                )
        );
    }

    @Test
    void shouldConsumeReservationForSameUser() {
        User user = createUser("user@example.com");
        FeatureUsageRecord record = reserve(user);
        TranslationJob translationJob = createJob(user);

        record.consume(translationJob);

        assertAll(
                () -> assertEquals(UsageStatus.CONSUMED, record.getStatus()),
                () -> assertSame(
                        translationJob,
                        record.getTranslationJob()
                ),
                () -> assertNull(record.getExpiresAt())
        );
    }

    @Test
    void shouldRejectMissingTranslationJobWithoutChangingReservation() {
        FeatureUsageRecord record = reserve(createUser("user@example.com"));

        assertThrows(
                IllegalArgumentException.class,
                () -> record.consume(null)
        );

        assertReserved(record);
    }

    @Test
    void shouldRejectTranslationJobOfAnotherUserWithoutChangingReservation() {
        FeatureUsageRecord record = reserve(createUser("owner@example.com"));
        TranslationJob foreignJob = createJob(
                createUser("foreign@example.com")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> record.consume(foreignJob)
        );

        assertReserved(record);
    }

    @Test
    void shouldReleaseReservation() {
        FeatureUsageRecord record = reserve(createUser("user@example.com"));

        record.release();

        assertAll(
                () -> assertEquals(UsageStatus.RELEASED, record.getStatus()),
                () -> assertNull(record.getTranslationJob()),
                () -> assertNull(record.getExpiresAt())
        );
    }

    @Test
    void shouldNotChangeConsumedRecord() {
        User user = createUser("user@example.com");
        FeatureUsageRecord record = reserve(user);
        TranslationJob translationJob = createJob(user);
        record.consume(translationJob);

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        record::release
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> record.consume(createJob(user))
                )
        );

        assertAll(
                () -> assertEquals(UsageStatus.CONSUMED, record.getStatus()),
                () -> assertSame(
                        translationJob,
                        record.getTranslationJob()
                ),
                () -> assertNull(record.getExpiresAt())
        );
    }

    @Test
    void shouldNotChangeReleasedRecord() {
        User user = createUser("user@example.com");
        FeatureUsageRecord record = reserve(user);
        record.release();

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        record::release
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> record.consume(createJob(user))
                )
        );

        assertAll(
                () -> assertEquals(UsageStatus.RELEASED, record.getStatus()),
                () -> assertNull(record.getTranslationJob()),
                () -> assertNull(record.getExpiresAt())
        );
    }

    private FeatureUsageRecord reserve(User user) {
        return FeatureUsageRecord.reserve(
                user,
                FeatureCode.DOCUMENT_TRANSLATION,
                1,
                PERIOD_START,
                PERIOD_END,
                EXPIRES_AT
        );
    }

    private User createUser(String email) {
        return new User(email, "password-hash");
    }

    private TranslationJob createJob(User user) {
        return new TranslationJob(
                user,
                "uploads/source.docx",
                "results/result.docx",
                "ru",
                "en",
                FileFormat.DOCX
        );
    }

    private void assertReserved(FeatureUsageRecord record) {
        assertAll(
                () -> assertEquals(UsageStatus.RESERVED, record.getStatus()),
                () -> assertNull(record.getTranslationJob()),
                () -> assertEquals(EXPIRES_AT, record.getExpiresAt())
        );
    }
}
