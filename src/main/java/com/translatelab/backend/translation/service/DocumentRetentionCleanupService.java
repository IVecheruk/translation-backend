package com.translatelab.backend.translation.service;

import com.translatelab.backend.config.DocumentRetentionProperties;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.storage.dto.StoredObjectInfo;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class DocumentRetentionCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DocumentRetentionCleanupService.class
    );
    private static final String ABANDONED_ERROR =
            "Время обработки задания истекло";
    private static final String SOURCE_PREFIX = "uploads/";
    private static final int ORPHAN_SCAN_MULTIPLIER = 10;

    private final TranslationJobRepository translationJobRepository;
    private final StorageService storageService;
    private final DocumentRetentionProperties properties;
    private final Clock clock;
    private final Counter abandonedCounter;
    private final Counter sourceCounter;
    private final Counter resultCounter;
    private final Counter orphanCounter;
    private final Counter failureCounter;

    public DocumentRetentionCleanupService(
            TranslationJobRepository translationJobRepository,
            StorageService storageService,
            DocumentRetentionProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.translationJobRepository = translationJobRepository;
        this.storageService = storageService;
        this.properties = properties;
        this.clock = clock;
        this.abandonedCounter = counter(meterRegistry, "abandoned_job");
        this.sourceCounter = counter(meterRegistry, "source");
        this.resultCounter = counter(meterRegistry, "result");
        this.orphanCounter = counter(meterRegistry, "orphan_upload");
        this.failureCounter = Counter.builder(
                        "translatelab.document.cleanup.failures"
                )
                .description("Document retention cleanup failures")
                .register(meterRegistry);
    }

    @Transactional
    public DocumentCleanupResult cleanupBatch() {
        Instant now = clock.instant();
        int remaining = properties.cleanupBatchSize();

        int abandoned = markAbandonedJobs(now, remaining);
        remaining -= abandoned;

        CleanupCount completedSources = cleanupSources(
                TranslationStatus.DONE,
                now.minus(properties.completedSourceRetention()),
                remaining
        );
        remaining -= completedSources.processed();

        CleanupCount failedSources = cleanupSources(
                TranslationStatus.FAILED,
                now.minus(properties.failedSourceRetention()),
                remaining
        );
        remaining -= failedSources.processed();

        CleanupCount results = cleanupResults(
                now.minus(properties.resultRetention()),
                remaining
        );
        remaining -= results.processed();

        CleanupCount orphanUploads = cleanupOrphanUploads(
                now.minus(properties.abandonedUploadRetention()),
                remaining
        );

        int failures = completedSources.failures()
                + failedSources.failures()
                + results.failures()
                + orphanUploads.failures();

        return new DocumentCleanupResult(
                abandoned,
                completedSources.succeeded() + failedSources.succeeded(),
                results.succeeded(),
                orphanUploads.succeeded(),
                failures
        );
    }

    private int markAbandonedJobs(Instant now, int limit) {
        if (limit <= 0) {
            return 0;
        }

        List<TranslationJob> jobs = translationJobRepository
                .findAbandonedForUpdate(
                        List.of(
                                TranslationStatus.PENDING,
                                TranslationStatus.PROCESSING
                        ),
                        now.minus(properties.abandonedJobTimeout()),
                        PageRequest.of(0, limit)
                );

        jobs.forEach(job -> job.fail(ABANDONED_ERROR));
        abandonedCounter.increment(jobs.size());
        return jobs.size();
    }

    private CleanupCount cleanupSources(
            TranslationStatus status,
            Instant cutoff,
            int limit
    ) {
        if (limit <= 0) {
            return CleanupCount.EMPTY;
        }

        List<TranslationJob> jobs = translationJobRepository
                .findSourcesForCleanup(
                        status,
                        cutoff,
                        PageRequest.of(0, limit)
                );

        int succeeded = 0;
        int failed = 0;

        for (TranslationJob job : jobs) {
            if (job.getStatus() != status) {
                recordFailure(
                        "source_state",
                        job,
                        new IllegalStateException(
                                "Статус кандидата очистки изменился"
                        )
                );
                failed++;
                continue;
            }

            try {
                storageService.delete(job.getSourceFileKey());
                job.markSourceDeleted(clock.instant());
                sourceCounter.increment();
                succeeded++;
            } catch (RuntimeException exception) {
                recordFailure("source", job, exception);
                failed++;
            }
        }

        return new CleanupCount(succeeded, failed);
    }

    private CleanupCount cleanupResults(Instant cutoff, int limit) {
        if (limit <= 0) {
            return CleanupCount.EMPTY;
        }

        List<TranslationJob> jobs = translationJobRepository
                .findResultsForCleanup(
                        cutoff,
                        PageRequest.of(0, limit)
                );

        int succeeded = 0;
        int failed = 0;

        for (TranslationJob job : jobs) {
            if (job.getStatus() != TranslationStatus.DONE) {
                recordFailure(
                        "result_state",
                        job,
                        new IllegalStateException(
                                "Статус кандидата очистки изменился"
                        )
                );
                failed++;
                continue;
            }

            try {
                storageService.delete(job.getResultFileKey());
                job.markResultDeleted(clock.instant());
                resultCounter.increment();
                succeeded++;
            } catch (RuntimeException exception) {
                recordFailure("result", job, exception);
                failed++;
            }
        }

        return new CleanupCount(succeeded, failed);
    }

    private CleanupCount cleanupOrphanUploads(Instant cutoff, int limit) {
        if (limit <= 0) {
            return CleanupCount.EMPTY;
        }

        List<StoredObjectInfo> candidates;
        try {
            candidates = storageService.listOlderThan(
                    SOURCE_PREFIX,
                    cutoff,
                    Math.multiplyExact(limit, ORPHAN_SCAN_MULTIPLIER)
            );
        } catch (RuntimeException exception) {
            failureCounter.increment();
            LOGGER.warn(
                    "Не удалось получить кандидатов для очистки брошенных загрузок",
                    exception
            );
            return new CleanupCount(0, 1);
        }

        int succeeded = 0;
        int failed = 0;

        for (StoredObjectInfo candidate : candidates) {
            if (succeeded + failed >= limit) {
                break;
            }

            if (translationJobRepository.existsBySourceFileKey(
                    candidate.objectKey()
            )) {
                continue;
            }

            try {
                storageService.delete(candidate.objectKey());
                orphanCounter.increment();
                succeeded++;
            } catch (RuntimeException exception) {
                failureCounter.increment();
                LOGGER.warn(
                        "Не удалось удалить брошенную загрузку",
                        exception
                );
                failed++;
            }
        }

        return new CleanupCount(succeeded, failed);
    }

    private void recordFailure(
            String kind,
            TranslationJob job,
            RuntimeException exception
    ) {
        failureCounter.increment();
        LOGGER.warn(
                "Не удалось выполнить очистку типа {} для задания {}",
                kind,
                job.getId(),
                exception
        );
    }

    private Counter counter(MeterRegistry meterRegistry, String kind) {
        return Counter.builder("translatelab.document.cleanup.objects")
                .tag("kind", kind)
                .description("Successfully processed document cleanup items")
                .register(meterRegistry);
    }

    private record CleanupCount(int succeeded, int failures) {

        private static final CleanupCount EMPTY = new CleanupCount(0, 0);

        private int processed() {
            return succeeded + failures;
        }
    }
}
