package com.translatelab.backend.translation.service;

import com.translatelab.backend.translation.dto.DocumentStatusResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.entity.TranslationErrorCode;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentStatusServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "584175c1-d670-4ef5-91e4-896ffb80b9cc"
    );
    private static final UUID JOB_ID = UUID.fromString(
            "10cf4338-5af4-47c0-b322-c17a283a9674"
    );

    @Mock
    private TranslationJobRepository translationJobRepository;

    private DocumentStatusService service;

    @BeforeEach
    void setUp() {
        service = new DocumentStatusService(
                translationJobRepository
        );
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, 0",
            "PROCESSING, 37",
            "DONE, 100",
            "FAILED, 62"
    })
    void shouldReturnPersistedProgress(
            TranslationStatus status,
            int expectedProgress
    ) {
        TranslationJob job = createJob(status, expectedProgress);
        given(translationJobRepository.findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        )).willReturn(Optional.of(job));

        DocumentStatusResponse response = service.getStatus(
                USER_ID,
                JOB_ID
        );

        assertAll(
                () -> assertEquals(JOB_ID, response.jobId()),
                () -> assertEquals(status, response.status()),
                () -> assertEquals(
                        expectedProgress,
                        response.progress()
                ),
                () -> {
                    if (status == TranslationStatus.FAILED) {
                        assertEquals(
                                "Не удалось перевести документ",
                                response.errorMessage()
                        );
                        assertEquals(
                                TranslationErrorCode.TRANSLATION_FAILED,
                                response.errorCode()
                        );
                    } else {
                        assertNull(response.errorMessage());
                        assertNull(response.errorCode());
                    }
                }
        );

        verify(translationJobRepository).findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        );
    }

    @Test
    void shouldHideMissingOrForeignJobBehindNotFoundError() {
        given(translationJobRepository.findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        )).willReturn(Optional.empty());

        assertThrows(
                TranslationJobNotFoundException.class,
                () -> service.getStatus(USER_ID, JOB_ID)
        );

        verify(translationJobRepository).findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        );
    }

    @Test
    void shouldRejectNullUserIdBeforeCallingRepository() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.getStatus(null, JOB_ID)
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(translationJobRepository);
    }

    @Test
    void shouldRejectNullJobIdBeforeCallingRepository() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.getStatus(USER_ID, null)
        );

        assertEquals(
                "Идентификатор задания не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(translationJobRepository);
    }

    private TranslationJob createJob(
            TranslationStatus status,
            int progress
    ) {
        TranslationJob job = new TranslationJob(
                new User(
                        "user@example.com",
                        "encoded-password"
                ),
                "uploads/user-id/document-id.docx",
                "results/user-id/document-id.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );
        ReflectionTestUtils.setField(job, "id", JOB_ID);

        switch (status) {
            case PENDING -> {
            }
            case PROCESSING -> {
                job.startProcessing();
                job.updateProgress(progress);
            }
            case DONE -> {
                job.startProcessing();
                job.complete();
            }
            case FAILED -> {
                job.startProcessing();
                job.updateProgress(progress);
                job.fail("Ошибка обработки документа");
            }
        }

        return job;
    }
}
