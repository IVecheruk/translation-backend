package com.translatelab.backend.usage.scheduler;

import com.translatelab.backend.config.UsageProperties;
import com.translatelab.backend.usage.service.UsageLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageReservationCleanupSchedulerTest {

    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final String CLEANUP_INTERVAL_PROPERTY =
            "${app.usage.cleanup-interval}";

    @Mock
    private UsageLimitService usageLimitService;

    private UsageReservationCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        UsageProperties usageProperties = new UsageProperties(
                Duration.ofMinutes(15),
                Duration.ofMinutes(1),
                CLEANUP_BATCH_SIZE
        );
        scheduler = new UsageReservationCleanupScheduler(
                usageLimitService,
                usageProperties
        );
    }

    @Test
    void shouldReleaseConfiguredBatchOfExpiredReservations() {
        given(usageLimitService.releaseExpiredReservations(
                CLEANUP_BATCH_SIZE
        )).willReturn(3);

        scheduler.cleanupExpiredReservations();

        verify(usageLimitService).releaseExpiredReservations(
                CLEANUP_BATCH_SIZE
        );
    }

    @Test
    void shouldHandleEmptyCleanupBatch() {
        given(usageLimitService.releaseExpiredReservations(
                CLEANUP_BATCH_SIZE
        )).willReturn(0);

        assertDoesNotThrow(
                scheduler::cleanupExpiredReservations
        );
        verify(usageLimitService).releaseExpiredReservations(
                CLEANUP_BATCH_SIZE
        );
    }

    @Test
    void shouldUseConfiguredInitialAndFixedDelay() throws Exception {
        Method cleanupMethod =
                UsageReservationCleanupScheduler.class.getDeclaredMethod(
                        "cleanupExpiredReservations"
                );

        Scheduled scheduled = cleanupMethod.getAnnotation(
                Scheduled.class
        );

        assertNotNull(scheduled);
        assertAll(
                () -> assertEquals(
                        CLEANUP_INTERVAL_PROPERTY,
                        scheduled.initialDelayString()
                ),
                () -> assertEquals(
                        CLEANUP_INTERVAL_PROPERTY,
                        scheduled.fixedDelayString()
                )
        );
    }
}
