package com.translatelab.backend.usage.service;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.usage.entity.FeatureUsageRecord;
import com.translatelab.backend.usage.entity.UsageStatus;
import com.translatelab.backend.usage.exception.UsageLimitExceededException;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(UsageLimitServiceIntegrationTest.FixedClockConfig.class)
class UsageLimitServiceIntegrationTest {

    private static final FeatureCode FEATURE_CODE =
            FeatureCode.DOCUMENT_TRANSLATION;
    private static final Instant NOW =
            Instant.parse("2026-07-17T15:42:31Z");
    private static final Instant PERIOD_START =
            Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant PERIOD_END =
            Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-07-17T15:57:31Z");

    @Autowired
    private UsageLimitService usageLimitService;

    @Autowired
    private FeatureUsageRecordRepository usageRecordRepository;

    @Autowired
    private TranslationJobRepository translationJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void shouldPersistCompleteReservationLifecycle() {
        User user = userRepository.saveAndFlush(createUser());

        UUID consumedReservationId = usageLimitService.reserve(
                user.getId(),
                FEATURE_CODE,
                1
        );
        FeatureUsageRecord reserved = usageRecordRepository
                .findById(consumedReservationId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        UsageStatus.RESERVED,
                        reserved.getStatus()
                ),
                () -> assertEquals(1, reserved.getUnits()),
                () -> assertEquals(
                        PERIOD_START,
                        reserved.getPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        reserved.getPeriodEnd()
                ),
                () -> assertEquals(
                        EXPIRES_AT,
                        reserved.getExpiresAt()
                ),
                () -> assertNull(reserved.getTranslationJob())
        );

        TranslationJob translationJob =
                translationJobRepository.saveAndFlush(
                        new TranslationJob(
                                user,
                                "uploads/source.docx",
                                "results/" + user.getId()
                                        + "/" + UUID.randomUUID()
                                        + ".docx",
                                "ru",
                                "en",
                                FileFormat.DOCX
                        )
                );

        usageLimitService.consume(
                consumedReservationId,
                translationJob.getId()
        );
        entityManager.flush();
        entityManager.clear();

        FeatureUsageRecord consumed = usageRecordRepository
                .findById(consumedReservationId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        UsageStatus.CONSUMED,
                        consumed.getStatus()
                ),
                () -> assertEquals(
                        translationJob.getId(),
                        consumed.getTranslationJob().getId()
                ),
                () -> assertNull(consumed.getExpiresAt())
        );

        UUID releasedReservationId = usageLimitService.reserve(
                user.getId(),
                FEATURE_CODE,
                1
        );
        usageLimitService.release(releasedReservationId);
        entityManager.flush();
        entityManager.clear();

        FeatureUsageRecord released = usageRecordRepository
                .findById(releasedReservationId)
                .orElseThrow();
        long occupiedUnits = usageRecordRepository.sumOccupiedUnits(
                user.getId(),
                FEATURE_CODE,
                PERIOD_START,
                PERIOD_END
        );

        assertAll(
                () -> assertEquals(
                        UsageStatus.RELEASED,
                        released.getStatus()
                ),
                () -> assertNull(released.getExpiresAt()),
                () -> assertNull(released.getTranslationJob()),
                () -> assertEquals(1, occupiedUnits)
        );
    }

    @Test
    void shouldAllowOnlyFiveParallelFreeReservations()
            throws Exception {
        User user = userRepository.saveAndFlush(createUser());
        UUID userId = user.getId();
        int requestCount = 6;
        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();

        try {
            for (int request = 0; request < requestCount; request++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Запуск параллельных запросов "
                                        + "превысил лимит ожидания"
                        );
                    }

                    return usageLimitService.reserve(
                            userId,
                            FEATURE_CODE,
                            1
                    );
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<UUID> successfulReservations = new ArrayList<>();
            int rejectedRequests = 0;

            for (Future<UUID> future : futures) {
                try {
                    successfulReservations.add(
                            future.get(10, TimeUnit.SECONDS)
                    );
                } catch (ExecutionException exception) {
                    if (exception.getCause()
                            instanceof UsageLimitExceededException) {
                        rejectedRequests++;
                    } else {
                        throw exception;
                    }
                }
            }

            Set<UUID> uniqueReservationIds =
                    new HashSet<>(successfulReservations);
            int rejectedRequestCount = rejectedRequests;
            long occupiedUnits = usageRecordRepository.sumOccupiedUnits(
                    userId,
                    FEATURE_CODE,
                    PERIOD_START,
                    PERIOD_END
            );

            assertAll(
                    () -> assertEquals(
                            5,
                            successfulReservations.size()
                    ),
                    () -> assertEquals(5, uniqueReservationIds.size()),
                    () -> assertEquals(1, rejectedRequestCount),
                    () -> assertEquals(5, occupiedUnits),
                    () -> assertTrue(
                            successfulReservations.stream()
                                    .allMatch(id -> id != null)
                    )
            );
        } finally {
            start.countDown();

            for (Future<UUID> future : futures) {
                future.cancel(true);
            }

            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            userRepository.deleteById(userId);
        }
    }

    private User createUser() {
        return new User(
                UUID.randomUUID() + "@example.com",
                "password-hash"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedUsageClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
