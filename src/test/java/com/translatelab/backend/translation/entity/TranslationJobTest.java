package com.translatelab.backend.translation.entity;

import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;

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
        assertNull(job.getErrorMessage());
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

        job.complete("results/result.docx");

        assertEquals(TranslationStatus.DONE, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals("results/result.docx", job.getResultFileKey());
        assertNull(job.getErrorMessage());
    }

    @Test
    void shouldNotCompletePendingJob() {
        TranslationJob job = createJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.complete("results/result.docx")
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
        assertNull(job.getResultFileKey());
    }

    @Test
    void shouldRejectBlankResultFileKey() {
        TranslationJob job = createJob();
        job.startProcessing();

        assertThrows(
                IllegalArgumentException.class,
                () -> job.complete("   ")
        );

        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
        assertNull(job.getResultFileKey());
    }

    @Test
    void shouldRejectTooLongResultFileKey() {
        TranslationJob job = createJob();
        job.startProcessing();
        String tooLongResultFileKey = "a".repeat(1025);

        assertThrows(
                IllegalArgumentException.class,
                () -> job.complete(tooLongResultFileKey)
        );

        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
        assertNull(job.getResultFileKey());
    }

    @Test
    void shouldFailPendingJob() {
        TranslationJob job = createJob();

        job.fail("  Ошибка загрузки файла  ");

        assertEquals(TranslationStatus.FAILED, job.getStatus());
        assertEquals(0, job.getProgress());
        assertEquals("Ошибка загрузки файла", job.getErrorMessage());
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
        assertEquals("Ошибка перевода", job.getErrorMessage());
        assertNull(job.getResultFileKey());
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
        job.complete("results/result.docx");

        assertThrows(
                IllegalStateException.class,
                () -> job.fail("Поздняя ошибка")
        );

        assertEquals(TranslationStatus.DONE, job.getStatus());
        assertEquals("results/result.docx", job.getResultFileKey());
        assertNull(job.getErrorMessage());
    }

    private TranslationJob createJob() {
        User user = new User("user@example.com", "password-hash");

        return new TranslationJob(
                user,
                "uploads/source.docx",
                "ru",
                "en",
                FileFormat.DOCX
        );
    }
}
