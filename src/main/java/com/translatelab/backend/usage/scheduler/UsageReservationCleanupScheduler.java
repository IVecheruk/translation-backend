package com.translatelab.backend.usage.scheduler;

import com.translatelab.backend.config.UsageProperties;
import com.translatelab.backend.usage.service.UsageLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UsageReservationCleanupScheduler {

    private static final Logger log = LoggerFactory
            .getLogger(UsageReservationCleanupScheduler.class);

    private final UsageLimitService usageLimitService;
    private final UsageProperties usageProperties;

    public UsageReservationCleanupScheduler(
            UsageLimitService usageLimitService,
            UsageProperties usageProperties
    ) {
        this.usageLimitService = usageLimitService;
        this.usageProperties = usageProperties;
    }

    @Scheduled(
            initialDelayString = "${app.usage.cleanup-interval}",
            fixedDelayString = "${app.usage.cleanup-interval}"
    )
    public void cleanupExpiredReservations() {
        int releasedCount = usageLimitService.releaseExpiredReservations(
                usageProperties.cleanupBatchSize()
        );

        if (releasedCount > 0) {
            log.info("Освобождено просроченных резерваций: {}", releasedCount);
        }
    }
}