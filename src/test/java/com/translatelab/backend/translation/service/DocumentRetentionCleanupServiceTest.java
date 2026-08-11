package com.translatelab.backend.translation.service;

import com.translatelab.backend.config.DocumentRetentionProperties;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.dto.StoredObjectInfo;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.user.entity.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentRetentionCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T06:00:00Z");

    @Mock
    private TranslationJobRepository repository;

    @Mock
    private StorageService storageService;

    private SimpleMeterRegistry meterRegistry;
    private DocumentRetentionCleanupService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new DocumentRetentionCleanupService(
                repository,
                storageService,
                properties(10),
                Clock.fixed(NOW, ZoneOffset.UTC),
                meterRegistry
        );
    }

    @Test
    void shouldProcessAbandonedAndTerminalJobsWithinOneBoundedBatch() {
        TranslationJob abandoned = pending("abandoned.docx");
        TranslationJob completed = completed(
                "completed.docx",
                "result.docx"
        );
        TranslationJob failed = failed("failed.pdf");

        given(repository.findAbandonedForUpdate(
                eq(List.of(
                        TranslationStatus.PENDING,
                        TranslationStatus.PROCESSING
                )),
                eq(NOW.minus(Duration.ofDays(1))),
                any(Pageable.class)
        )).willReturn(List.of(abandoned));
        given(repository.findSourcesForCleanup(
                eq(TranslationStatus.DONE),
                eq(NOW.minus(Duration.ofDays(1))),
                any(Pageable.class)
        )).willReturn(List.of(completed));
        given(repository.findSourcesForCleanup(
                eq(TranslationStatus.FAILED),
                eq(NOW.minus(Duration.ofDays(2))),
                any(Pageable.class)
        )).willReturn(List.of(failed));
        given(repository.findResultsForCleanup(
                eq(NOW.minus(Duration.ofDays(30))),
                any(Pageable.class)
        )).willReturn(List.of(completed));

        DocumentCleanupResult result = service.cleanupBatch();

        assertAll(
                () -> assertEquals(1, result.abandonedJobs()),
                () -> assertEquals(2, result.deletedSources()),
                () -> assertEquals(1, result.deletedResults()),
                () -> assertEquals(0, result.failures()),
                () -> assertEquals(
                        TranslationStatus.FAILED,
                        abandoned.getStatus()
                ),
                () -> assertNotNull(completed.getSourceDeletedAt()),
                () -> assertNotNull(completed.getResultDeletedAt()),
                () -> assertNotNull(failed.getSourceDeletedAt())
        );
        verify(storageService).delete("completed.docx");
        verify(storageService).delete("failed.pdf");
        verify(storageService).delete("result.docx");
        assertEquals(
                2.0,
                meterRegistry.get("translatelab.document.cleanup.objects")
                        .tag("kind", "source")
                        .counter()
                        .count()
        );
    }

    @Test
    void shouldRetainMarkerAndCountFailureWhenStorageDeleteFails() {
        TranslationJob failed = failed("failed.docx");
        given(repository.findAbandonedForUpdate(
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(repository.findSourcesForCleanup(
                eq(TranslationStatus.DONE),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(repository.findSourcesForCleanup(
                eq(TranslationStatus.FAILED),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(failed));
        given(repository.findResultsForCleanup(
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        doThrow(new StorageException(
                "delete failed",
                new IOExceptionWithoutCheckedType()
        )).when(storageService).delete("failed.docx");

        DocumentCleanupResult result = service.cleanupBatch();

        assertAll(
                () -> assertEquals(0, result.deletedSources()),
                () -> assertEquals(1, result.failures()),
                () -> assertNull(failed.getSourceDeletedAt()),
                () -> assertEquals(
                        1.0,
                        meterRegistry.get(
                                        "translatelab.document.cleanup.failures"
                                )
                                .counter()
                                .count()
                )
        );
    }

    @Test
    void shouldNeverDeleteSourceReturnedWithActiveStatus() {
        TranslationJob active = pending("active.docx");
        given(repository.findAbandonedForUpdate(
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(repository.findSourcesForCleanup(
                eq(TranslationStatus.DONE),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(active));
        given(repository.findSourcesForCleanup(
                eq(TranslationStatus.FAILED),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(repository.findResultsForCleanup(
                any(),
                any(Pageable.class)
        )).willReturn(List.of());

        DocumentCleanupResult result = service.cleanupBatch();

        assertEquals(1, result.failures());
        verify(storageService, never()).delete("active.docx");
        assertNull(active.getSourceDeletedAt());
    }

    @Test
    void shouldStopAfterConfiguredBatchSize() {
        DocumentRetentionCleanupService oneItemService =
                new DocumentRetentionCleanupService(
                        repository,
                        storageService,
                        properties(1),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        meterRegistry
                );
        given(repository.findAbandonedForUpdate(
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(pending("active.docx")));

        DocumentCleanupResult result = oneItemService.cleanupBatch();

        assertEquals(1, result.processed());
        verify(repository, never()).findSourcesForCleanup(
                any(),
                any(),
                any(Pageable.class)
        );
        verify(repository, never()).findResultsForCleanup(
                any(),
                any(Pageable.class)
        );
    }

    @Test
    void shouldDeleteOnlyOldUnreferencedUploads() {
        StoredObjectInfo referenced = new StoredObjectInfo(
                "uploads/user/referenced.docx",
                NOW.minus(Duration.ofDays(2))
        );
        StoredObjectInfo orphan = new StoredObjectInfo(
                "uploads/user/orphan.docx",
                NOW.minus(Duration.ofDays(2))
        );
        given(repository.findAbandonedForUpdate(
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(repository.findSourcesForCleanup(
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(repository.findResultsForCleanup(
                any(),
                any(Pageable.class)
        )).willReturn(List.of());
        given(storageService.listOlderThan(
                eq("uploads/"),
                eq(NOW.minus(Duration.ofDays(1))),
                eq(100)
        )).willReturn(List.of(referenced, orphan));
        given(repository.existsBySourceFileKey(referenced.objectKey()))
                .willReturn(true);

        DocumentCleanupResult result = service.cleanupBatch();

        assertAll(
                () -> assertEquals(1, result.deletedOrphanUploads()),
                () -> assertEquals(0, result.failures())
        );
        verify(storageService, never()).delete(referenced.objectKey());
        verify(storageService).delete(orphan.objectKey());
    }

    private DocumentRetentionProperties properties(int batchSize) {
        return new DocumentRetentionProperties(
                Duration.ofDays(1),
                Duration.ofDays(2),
                Duration.ofDays(30),
                Duration.ofDays(1),
                Duration.ofDays(1),
                Duration.ofMinutes(10),
                batchSize
        );
    }

    private TranslationJob pending(String sourceKey) {
        return new TranslationJob(
                new User("test@example.com", "password"),
                sourceKey,
                "results/test/result.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );
    }

    private TranslationJob completed(String sourceKey, String resultKey) {
        TranslationJob job = new TranslationJob(
                new User("test@example.com", "password"),
                sourceKey,
                resultKey,
                "en",
                "ru",
                FileFormat.DOCX
        );
        job.startProcessing();
        job.complete();
        return job;
    }

    private TranslationJob failed(String sourceKey) {
        TranslationJob job = pending(sourceKey);
        job.fail("failed");
        return job;
    }

    private static final class IOExceptionWithoutCheckedType
            extends RuntimeException {
    }
}
