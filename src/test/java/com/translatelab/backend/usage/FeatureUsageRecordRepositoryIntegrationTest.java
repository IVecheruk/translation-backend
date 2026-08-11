package com.translatelab.backend.usage;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.usage.entity.FeatureUsageRecord;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class FeatureUsageRecordRepositoryIntegrationTest {

    private static final Instant PERIOD_START =
            Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant PERIOD_END =
            Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-07-30T12:15:00Z");

    @Autowired
    private FeatureUsageRecordRepository usageRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TranslationJobRepository translationJobRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSumOnlyOccupiedUnitsForRequestedUserFeatureAndPeriod() {
        User owner = saveUser();
        User anotherUser = saveUser();

        FeatureUsageRecord reserved = reserve(owner, 1);
        FeatureUsageRecord consumed = reserve(owner, 2);
        TranslationJob translationJob = translationJobRepository.save(
                createJob(owner)
        );
        consumed.consume(translationJob);

        FeatureUsageRecord released = reserve(owner, 4);
        released.release();

        FeatureUsageRecord anotherUsersRecord = reserve(anotherUser, 8);

        FeatureUsageRecord anotherPeriodsRecord =
                FeatureUsageRecord.reserve(
                        owner,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        16,
                        PERIOD_END,
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-08-01T00:15:00Z")
                );

        usageRecordRepository.saveAllAndFlush(
                java.util.List.of(
                        reserved,
                        consumed,
                        released,
                        anotherUsersRecord,
                        anotherPeriodsRecord
                )
        );

        long occupiedUnits = usageRecordRepository.sumOccupiedUnits(
                owner.getId(),
                FeatureCode.DOCUMENT_TRANSLATION,
                PERIOD_START,
                PERIOD_END
        );

        assertEquals(3, occupiedUnits);
    }

    @Test
    void shouldReturnZeroWhenNoUsageRecordsMatch() {
        User user = saveUser();

        long occupiedUnits = usageRecordRepository.sumOccupiedUnits(
                user.getId(),
                FeatureCode.DOCUMENT_TRANSLATION,
                PERIOD_START,
                PERIOD_END
        );

        assertEquals(0, occupiedUnits);
    }

    @Test
    void shouldPersistAndReloadReservedRecord() {
        User user = saveUser();
        FeatureUsageRecord saved = usageRecordRepository.saveAndFlush(
                reserve(user, 1)
        );
        UUID recordId = saved.getId();
        entityManager.clear();

        FeatureUsageRecord reloaded = usageRecordRepository
                .findById(recordId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(recordId, reloaded.getId()),
                () -> assertEquals(user.getId(), reloaded.getUser().getId()),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        reloaded.getFeatureCode()
                ),
                () -> assertEquals(1, reloaded.getUnits()),
                () -> assertEquals(PERIOD_START, reloaded.getPeriodStart()),
                () -> assertEquals(PERIOD_END, reloaded.getPeriodEnd()),
                () -> assertEquals(
                        com.translatelab.backend.usage.entity.UsageStatus.RESERVED,
                        reloaded.getStatus()
                ),
                () -> assertNull(reloaded.getTranslationJob()),
                () -> assertEquals(EXPIRES_AT, reloaded.getExpiresAt()),
                () -> assertNotNull(reloaded.getCreatedAt()),
                () -> assertNotNull(reloaded.getUpdatedAt())
        );
    }

    @Test
    void shouldFindOnlyExpiredReservationsInOrderedLimitedBatch() {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        User user = saveUser();

        FeatureUsageRecord oldestExpired = reserve(
                user,
                1,
                Instant.parse("2026-07-30T10:00:00Z")
        );
        FeatureUsageRecord expiredAtBoundary = reserve(user, 1, now);
        FeatureUsageRecord active = reserve(
                user,
                1,
                Instant.parse("2026-07-30T13:00:00Z")
        );
        FeatureUsageRecord released = reserve(
                user,
                1,
                Instant.parse("2026-07-30T09:00:00Z")
        );
        released.release();

        FeatureUsageRecord consumed = reserve(
                user,
                1,
                Instant.parse("2026-07-30T08:00:00Z")
        );
        TranslationJob translationJob = translationJobRepository.save(
                createJob(user)
        );
        consumed.consume(translationJob);

        usageRecordRepository.saveAllAndFlush(
                List.of(
                        active,
                        expiredAtBoundary,
                        released,
                        consumed,
                        oldestExpired
                )
        );

        List<FeatureUsageRecord> limitedBatch =
                usageRecordRepository.findExpiredReservationsForUpdate(
                        now,
                        PageRequest.of(0, 1)
                );
        List<FeatureUsageRecord> allExpired =
                usageRecordRepository.findExpiredReservationsForUpdate(
                        now,
                        PageRequest.of(0, 10)
                );

        assertAll(
                () -> assertEquals(1, limitedBatch.size()),
                () -> assertEquals(
                        oldestExpired.getId(),
                        limitedBatch.getFirst().getId()
                ),
                () -> assertEquals(
                        List.of(
                                oldestExpired.getId(),
                                expiredAtBoundary.getId()
                        ),
                        allExpired.stream()
                                .map(FeatureUsageRecord::getId)
                                .toList()
                )
        );
    }

    private FeatureUsageRecord reserve(User user, int units) {
        return reserve(user, units, EXPIRES_AT);
    }

    private FeatureUsageRecord reserve(
            User user,
            int units,
            Instant expiresAt
    ) {
        return FeatureUsageRecord.reserve(
                user,
                FeatureCode.DOCUMENT_TRANSLATION,
                units,
                PERIOD_START,
                PERIOD_END,
                expiresAt
        );
    }

    private User saveUser() {
        return userRepository.save(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
    }

    private TranslationJob createJob(User user) {
        return new TranslationJob(
                user,
                "uploads/source.docx",
                "results/" + user.getId() + "/"
                        + UUID.randomUUID() + ".docx",
                "ru",
                "en",
                FileFormat.DOCX
        );
    }
}
