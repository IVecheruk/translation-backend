package com.translatelab.backend.translation.entity;

import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslationJobTest {

    @Test
    void newJobShouldHavePendingStatus() {
        TranslationJob job = createJob();

        assertEquals(TranslationStatus.PENDING, job.getStatus());
        assertEquals(0, job.getProgress());
        assertNull(job.getResultFileKey());
        assertNull(job.getErrorDetail());
        assertNull(job.getErrorCode());
        assertNull(job.getSourceDeletedAt());
        assertNull(job.getResultDeletedAt());
    }

    @Test
    void shouldStartProcessingPendingJob() {
        TranslationJob job = createJob();

        job.startProcessing();

        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
        assertEquals(0, job.getProgress());
    }

    @Test
    void shouldNotStartProcessingTwice() {
        TranslationJob job = createJob();
        job.startProcessing();

        assertThrows(IllegalStateException.class, job::startProcessing);

        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
    }

    @Test
    void shouldCompleteProcessingJob() {
        TranslationJob job = createJob();
        job.startProcessing();

        job.complete();

        assertEquals(TranslationStatus.DONE, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals("results/result.docx", job.getResultFileKey());
        assertNull(job.getErrorDetail());
        assertNull(job.getErrorCode());
    }

    @Test
    void shouldNotCompletePendingJob() {
        TranslationJob job = createJob();

        assertThrows(
                IllegalStateException.class,
                job::complete
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
        assertNull(job.getResultFileKey());
    }

    @Test
    void shouldRejectBlankExpectedResultFileKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TranslationJob(
                        new User("user@example.com", "password-hash"),
                        "uploads/source.docx",
                        "   ",
                        "ru",
                        "en",
                        FileFormat.DOCX
                )
        );
    }

    @Test
    void shouldRejectTooLongExpectedResultFileKey() {
        String tooLongResultFileKey = "a".repeat(1025);

        assertThrows(
                IllegalArgumentException.class,
                () -> new TranslationJob(
                        new User("user@example.com", "password-hash"),
                        "uploads/source.docx",
                        tooLongResultFileKey,
                        "ru",
                        "en",
                        FileFormat.DOCX
                )
        );
    }

    @Test
    void shouldFailPendingJob() {
        TranslationJob job = createJob();

        job.fail("  Ошибка загрузки файла  ");

        assertEquals(TranslationStatus.FAILED, job.getStatus());
        assertEquals(0, job.getProgress());
        assertEquals("Ошибка загрузки файла", job.getErrorDetail());
        assertEquals(
                TranslationErrorCode.TRANSLATION_FAILED,
                job.getErrorCode()
        );
        assertNull(job.getResultFileKey());
    }

    @Test
    void shouldFailProcessingJob() {
        TranslationJob job = createJob();
        job.startProcessing();
        job.updateProgress(47);

        job.fail("Ошибка перевода");

        assertEquals(TranslationStatus.FAILED, job.getStatus());
        assertEquals(47, job.getProgress());
        assertEquals("Ошибка перевода", job.getErrorDetail());
        assertNull(job.getResultFileKey());
    }

    @Test
    void shouldRejectDiagnosticDetailAboveDomainLimit() {
        TranslationJob job = createJob();

        assertThrows(
                IllegalArgumentException.class,
                () -> job.fail(
                        "x".repeat(
                                TranslationJob.MAX_ERROR_DETAIL_LENGTH + 1
                        )
                )
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
        assertNull(job.getErrorCode());
        assertNull(job.getErrorDetail());
    }

    @Test
    void shouldIncreaseProgressWhileProcessing() {
        TranslationJob job = createJob();
        job.startProcessing();

        job.updateProgress(25);
        job.updateProgress(70);

        assertEquals(70, job.getProgress());
        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
    }

    @Test
    void shouldRejectProgressOutsideProcessingRange() {
        TranslationJob job = createJob();
        job.startProcessing();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> job.updateProgress(-1)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> job.updateProgress(100)
                )
        );

        assertEquals(0, job.getProgress());
    }

    @Test
    void shouldRejectDecreasingProgress() {
        TranslationJob job = createJob();
        job.startProcessing();
        job.updateProgress(60);

        assertThrows(
                IllegalArgumentException.class,
                () -> job.updateProgress(59)
        );

        assertEquals(60, job.getProgress());
    }

    @Test
    void shouldRejectProgressUpdateForPendingJob() {
        TranslationJob job = createJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.updateProgress(10)
        );

        assertEquals(0, job.getProgress());
        assertEquals(TranslationStatus.PENDING, job.getStatus());
    }

    @Test
    void shouldNotFailCompletedJob() {
        TranslationJob job = createJob();
        job.startProcessing();
        job.complete();

        assertThrows(
                IllegalStateException.class,
                () -> job.fail("Поздняя ошибка")
        );

        assertEquals(TranslationStatus.DONE, job.getStatus());
        assertEquals("results/result.docx", job.getResultFileKey());
        assertNull(job.getErrorDetail());
    }

    @Test
    void shouldMarkTerminalFilesDeletedIdempotently() {
        TranslationJob job = createJob();
        job.startProcessing();
        job.complete();
        Instant first = Instant.parse("2026-08-08T06:00:00Z");

        job.markSourceDeleted(first);
        job.markSourceDeleted(first.plusSeconds(1));
        job.markResultDeleted(first);
        job.markResultDeleted(first.plusSeconds(1));

        assertEquals(first, job.getSourceDeletedAt());
        assertEquals(first, job.getResultDeletedAt());
    }

    @Test
    void shouldNeverMarkActiveSourceDeleted() {
        TranslationJob job = createJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.markSourceDeleted(Instant.now())
        );

        assertNull(job.getSourceDeletedAt());
    }

    private TranslationJob createJob() {
        User user = new User("user@example.com", "password-hash");

        return new TranslationJob(
                user,
                "uploads/source.docx",
                "results/result.docx",
                "ru",
                "en",
                FileFormat.DOCX
        );
    }
}
