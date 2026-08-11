package com.translatelab.backend.translation.scheduler;

import com.translatelab.backend.translation.service.DocumentCleanupResult;
import com.translatelab.backend.translation.service.DocumentRetentionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentRetentionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DocumentRetentionCleanupScheduler.class
    );

    private final DocumentRetentionCleanupService cleanupService;

    public DocumentRetentionCleanupScheduler(
            DocumentRetentionCleanupService cleanupService
    ) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            initialDelayString = "${app.document-retention.cleanup-interval}",
            fixedDelayString = "${app.document-retention.cleanup-interval}"
    )
    public void cleanupDocuments() {
        DocumentCleanupResult result = cleanupService.cleanupBatch();

        if (result.processed() > 0) {
            LOGGER.info(
                    "Очистка документов: брошено заданий {}, удалено исходников {}, "
                            + "удалено результатов {}, удалено брошенных загрузок {}, "
                            + "ошибок {}",
                    result.abandonedJobs(),
                    result.deletedSources(),
                    result.deletedResults(),
                    result.deletedOrphanUploads(),
                    result.failures()
            );
        }
    }
}
