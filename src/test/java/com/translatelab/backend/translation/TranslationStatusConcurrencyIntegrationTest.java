package com.translatelab.backend.translation;

import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.messaging.exception.InvalidTranslationStatusMessageException;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.translation.service.ResultDocumentValidationService;
import com.translatelab.backend.translation.service.TranslationStatusUpdateService;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class TranslationStatusConcurrencyIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TranslationJobRepository translationJobRepository;

    @Autowired
    private TranslationStatusUpdateService statusUpdateService;

    @MockitoBean
    private ResultDocumentValidationService resultValidationService;

    private UUID userId;
    private UUID jobId;

    @AfterEach
    void cleanUp() {
        if (jobId != null) {
            translationJobRepository.deleteById(jobId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void concurrentProgressEventsShouldKeepMaximumProgress()
            throws Exception {
        createJob();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> lower = executor.submit(() -> {
                await(start);
                statusUpdateService.updateStatus(processing(40));
            });
            Future<?> higher = executor.submit(() -> {
                await(start);
                statusUpdateService.updateStatus(processing(80));
            });

            start.countDown();
            lower.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            higher.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        TranslationJob stored = translationJobRepository
                .findById(jobId)
                .orElseThrow();
        assertEquals(TranslationStatus.PROCESSING, stored.getStatus());
        assertEquals(80, stored.getProgress());
    }

    @Test
    void doneShouldWinWhileConcurrentFailedWaitsForRowLock()
            throws Exception {
        TranslationJob job = createJob();
        CountDownLatch validationEntered = new CountDownLatch(1);
        CountDownLatch releaseValidation = new CountDownLatch(1);
        CountDownLatch failedStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            validationEntered.countDown();
            await(releaseValidation);
            return null;
        }).when(resultValidationService).validate(any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> done = executor.submit(() ->
                    statusUpdateService.updateStatus(done(job))
            );
            assertEquals(
                    true,
                    validationEntered.await(
                            TIMEOUT.toSeconds(),
                            TimeUnit.SECONDS
                    )
            );

            Future<?> failed = executor.submit(() -> {
                failedStarted.countDown();
                statusUpdateService.updateStatus(failed());
            });
            assertEquals(
                    true,
                    failedStarted.await(
                            TIMEOUT.toSeconds(),
                            TimeUnit.SECONDS
                    )
            );
            Thread.sleep(150);
            assertFalse(failed.isDone());

            releaseValidation.countDown();
            done.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> failed.get(
                            TIMEOUT.toSeconds(),
                            TimeUnit.SECONDS
                    )
            );
            assertInstanceOf(
                    InvalidTranslationStatusMessageException.class,
                    exception.getCause()
            );
        } finally {
            releaseValidation.countDown();
            executor.shutdownNow();
        }

        TranslationJob stored = translationJobRepository
                .findById(jobId)
                .orElseThrow();
        assertEquals(TranslationStatus.DONE, stored.getStatus());
        assertEquals(100, stored.getProgress());
        assertEquals(
                job.getExpectedResultFileKey(),
                stored.getResultFileKey()
        );
    }

    private TranslationJob createJob() {
        User user = userRepository.save(new User(
                "status-concurrency-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        ));
        userId = user.getId();
        TranslationJob job = translationJobRepository.saveAndFlush(
                new TranslationJob(
                        user,
                        "uploads/" + userId + "/source.docx",
                        "results/" + userId + "/"
                                + UUID.randomUUID() + ".docx",
                        "en",
                        "ru",
                        FileFormat.DOCX
                )
        );
        jobId = job.getId();
        return job;
    }

    private TranslationStatusMessage processing(int progress) {
        return new TranslationStatusMessage(
                jobId,
                TranslationStatus.PROCESSING,
                progress,
                null,
                null
        );
    }

    private TranslationStatusMessage done(TranslationJob job) {
        return new TranslationStatusMessage(
                jobId,
                TranslationStatus.DONE,
                100,
                job.getExpectedResultFileKey(),
                null
        );
    }

    private TranslationStatusMessage failed() {
        return new TranslationStatusMessage(
                jobId,
                TranslationStatus.FAILED,
                70,
                null,
                "internal ML trace"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
