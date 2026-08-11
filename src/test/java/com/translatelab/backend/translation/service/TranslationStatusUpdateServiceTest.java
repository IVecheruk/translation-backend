package com.translatelab.backend.translation.service;

import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.messaging.exception.InvalidTranslationStatusMessageException;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class TranslationStatusUpdateServiceTest {

    private static final UUID JOB_ID = UUID.fromString(
            "10cf4338-5af4-47c0-b322-c17a283a9674"
    );

    @Mock
    private TranslationJobRepository translationJobRepository;

    @Mock
    private ResultDocumentValidationService resultValidationService;

    private TranslationStatusUpdateService service;

    @BeforeEach
    void setUp() {
        service = new TranslationStatusUpdateService(
                translationJobRepository,
                resultValidationService
        );
    }

    @Test
    void shouldStartProcessingAndPersistReportedProgress() {
        TranslationJob job = pendingJob();
        givenJob(job);

        service.updateStatus(message(
                TranslationStatus.PROCESSING,
                35,
                null,
                null
        ));

        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
        assertEquals(35, job.getProgress());
    }

    @Test
    void shouldNotDecreaseProgressForStaleProcessingMessage() {
        TranslationJob job = pendingJob();
        job.startProcessing();
        job.updateProgress(70);
        givenJob(job);

        service.updateStatus(message(
                TranslationStatus.PROCESSING,
                45,
                null,
                null
        ));

        assertEquals(TranslationStatus.PROCESSING, job.getStatus());
        assertEquals(70, job.getProgress());
    }

    @Test
    void shouldCompletePendingJobWhenProcessingEventWasNotReceived() {
        TranslationJob job = pendingJob();
        givenJob(job);

        service.updateStatus(message(
                TranslationStatus.DONE,
                100,
                "results/user-id/result.docx",
                null
        ));

        assertAll(
                () -> assertEquals(
                        TranslationStatus.DONE,
                        job.getStatus()
                ),
                () -> assertEquals(100, job.getProgress()),
                () -> assertEquals(
                        "results/user-id/result.docx",
                        job.getResultFileKey()
                ),
                () -> assertNull(job.getErrorDetail())
        );
        verify(resultValidationService).validate(
                "results/user-id/result.docx",
                FileFormat.DOCX
        );
    }

    @Test
    void shouldFailProcessingJobAndKeepLatestProgress() {
        TranslationJob job = pendingJob();
        job.startProcessing();
        job.updateProgress(40);
        givenJob(job);

        service.updateStatus(message(
                TranslationStatus.FAILED,
                68,
                null,
                "  Ошибка ML-сервиса  "
        ));

        assertAll(
                () -> assertEquals(
                        TranslationStatus.FAILED,
                        job.getStatus()
                ),
                () -> assertEquals(68, job.getProgress()),
                () -> assertEquals(
                        "Ошибка ML-сервиса",
                        job.getErrorDetail()
                ),
                () -> assertNull(job.getResultFileKey())
        );
    }

    @Test
    void shouldAcceptDuplicateDoneMessage() {
        TranslationJob job = pendingJob();
        job.startProcessing();
        job.complete();
        givenJob(job);

        service.updateStatus(message(
                TranslationStatus.DONE,
                100,
                "results/user-id/result.docx",
                null
        ));

        assertEquals(TranslationStatus.DONE, job.getStatus());
        assertEquals(100, job.getProgress());
    }

    @Test
    void shouldIgnoreLateProcessingMessageForTerminalJob() {
        TranslationJob job = pendingJob();
        job.startProcessing();
        job.complete();
        givenJob(job);

        service.updateStatus(message(
                TranslationStatus.PROCESSING,
                80,
                null,
                null
        ));

        assertEquals(TranslationStatus.DONE, job.getStatus());
        assertEquals(100, job.getProgress());
    }

    @Test
    void shouldRejectPendingStatusFromMlService() {
        TranslationJob job = pendingJob();
        givenJob(job);

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.PENDING,
                        0,
                        null,
                        null
                ))
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
    }

    @Test
    void shouldRejectDoneWithoutResultFileKey() {
        TranslationJob job = pendingJob();
        givenJob(job);

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.DONE,
                        100,
                        null,
                        null
                ))
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
    }

    @Test
    void shouldRejectTooLongResultFileKey() {
        TranslationJob job = pendingJob();
        givenJob(job);

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.DONE,
                        100,
                        "a".repeat(1025),
                        null
                ))
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
    }

    @Test
    void shouldRejectInvalidProcessingProgress() {
        TranslationJob job = pendingJob();
        givenJob(job);

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.PROCESSING,
                        100,
                        null,
                        null
                ))
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
    }

    @Test
    void shouldRejectConflictingTerminalStatus() {
        TranslationJob job = pendingJob();
        job.fail("Ошибка перевода");
        givenJob(job);

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.DONE,
                        100,
                        "results/user-id/result.docx",
                        null
                ))
        );

        assertEquals(TranslationStatus.FAILED, job.getStatus());
    }

    @Test
    void shouldRejectMessageForUnknownJob() {
        given(translationJobRepository.findByIdForUpdate(JOB_ID))
                .willReturn(Optional.empty());

        assertThrows(
                TranslationJobNotFoundException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.PROCESSING,
                        10,
                        null,
                        null
                ))
        );
    }

    @Test
    void shouldRejectNullMessageBeforeCallingRepository() {
        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(null)
        );

        verifyNoInteractions(translationJobRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "uploads/another-user/source.docx",
            "results/another-user/result.docx",
            "avatars/another-user/avatar.png"
    })
    void shouldRejectDoneWithUnrelatedObjectKey(String unrelatedKey) {
        TranslationJob job = pendingJob();
        givenJob(job);

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.DONE,
                        100,
                        unrelatedKey,
                        null
                ))
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
        verifyNoInteractions(resultValidationService);
    }

    @Test
    void shouldRejectDoneWhenStoredResultHasWrongFormat() {
        TranslationJob job = pendingJob();
        givenJob(job);
        doThrow(new InvalidDocumentContentException())
                .when(resultValidationService)
                .validate(
                        "results/user-id/result.docx",
                        FileFormat.DOCX
                );

        assertThrows(
                InvalidTranslationStatusMessageException.class,
                () -> service.updateStatus(message(
                        TranslationStatus.DONE,
                        100,
                        "results/user-id/result.docx",
                        null
                ))
        );

        assertEquals(TranslationStatus.PENDING, job.getStatus());
    }

    private TranslationJob pendingJob() {
        TranslationJob job = new TranslationJob(
                new User(
                        "user@example.com",
                        "encoded-password"
                ),
                "uploads/user-id/document-id.docx",
                "results/user-id/result.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }

    private void givenJob(TranslationJob job) {
        given(translationJobRepository.findByIdForUpdate(JOB_ID))
                .willReturn(Optional.of(job));
    }

    private TranslationStatusMessage message(
            TranslationStatus status,
            int progress,
            String resultFileKey,
            String errorMessage
    ) {
        return new TranslationStatusMessage(
                JOB_ID,
                status,
                progress,
                resultFileKey,
                errorMessage
        );
    }
}
