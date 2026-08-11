package com.translatelab.backend.translation.service;

import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.dto.DocumentDownloadResult;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.exception.TranslationResultNotReadyException;
import com.translatelab.backend.translation.exception.TranslationResultExpiredException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentDownloadServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "584175c1-d670-4ef5-91e4-896ffb80b9cc"
    );
    private static final UUID JOB_ID = UUID.fromString(
            "10cf4338-5af4-47c0-b322-c17a283a9674"
    );

    @Mock
    private TranslationJobRepository translationJobRepository;

    @Mock
    private StorageService storageService;

    private DocumentDownloadService service;

    @BeforeEach
    void setUp() {
        service = new DocumentDownloadService(
                translationJobRepository,
                storageService
        );
    }

    @ParameterizedTest
    @CsvSource({
            "DOCX, application/vnd.openxmlformats-officedocument"
                    + ".wordprocessingml.document",
            "DOC, application/msword",
            "PDF, application/pdf"
    })
    void shouldDownloadCompletedTranslation(
            FileFormat format,
            String expectedContentType
    ) {
        TranslationJob job = completedJob(format);
        String resultFileKey = job.getResultFileKey();
        InputStream inputStream =
                new ByteArrayInputStream(new byte[]{1, 2, 3});
        given(translationJobRepository.findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        )).willReturn(Optional.of(job));
        given(storageService.download(resultFileKey))
                .willReturn(inputStream);

        DocumentDownloadResult result =
                service.download(USER_ID, JOB_ID);

        assertAll(
                () -> assertSame(
                        inputStream,
                        result.inputStream()
                ),
                () -> assertEquals(
                        "translation-" + JOB_ID
                                + "." + format.jsonValue(),
                        result.fileName()
                ),
                () -> assertEquals(
                        expectedContentType,
                        result.contentType()
                )
        );
        verify(storageService).download(resultFileKey);
    }

    @ParameterizedTest
    @EnumSource(
            value = TranslationStatus.class,
            names = "DONE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void shouldRejectTranslationWithoutCompletedResult(
            TranslationStatus status
    ) {
        TranslationJob job = jobWithStatus(status);
        given(translationJobRepository.findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        )).willReturn(Optional.of(job));

        assertThrows(
                TranslationResultNotReadyException.class,
                () -> service.download(USER_ID, JOB_ID)
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldHideMissingOrForeignJob() {
        given(translationJobRepository.findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        )).willReturn(Optional.empty());

        assertThrows(
                TranslationJobNotFoundException.class,
                () -> service.download(USER_ID, JOB_ID)
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectExpiredResultWithoutCallingStorage() {
        TranslationJob job = completedJob(FileFormat.PDF);
        job.markResultDeleted(Instant.parse("2026-08-08T06:00:00Z"));
        given(translationJobRepository.findByIdAndUser_Id(
                JOB_ID,
                USER_ID
        )).willReturn(Optional.of(job));

        assertThrows(
                TranslationResultExpiredException.class,
                () -> service.download(USER_ID, JOB_ID)
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectNullUserIdBeforeCallingDependencies() {
        assertThrows(
                NullPointerException.class,
                () -> service.download(null, JOB_ID)
        );

        verifyNoInteractions(
                translationJobRepository,
                storageService
        );
    }

    @Test
    void shouldRejectNullJobIdBeforeCallingDependencies() {
        assertThrows(
                NullPointerException.class,
                () -> service.download(USER_ID, null)
        );

        verifyNoInteractions(
                translationJobRepository,
                storageService
        );
    }

    private TranslationJob completedJob(FileFormat format) {
        TranslationJob job = newJob(format);
        job.startProcessing();
        job.complete();
        return job;
    }

    private TranslationJob jobWithStatus(
            TranslationStatus status
    ) {
        TranslationJob job = newJob(FileFormat.DOCX);

        switch (status) {
            case PENDING -> {
            }
            case PROCESSING -> job.startProcessing();
            case FAILED -> job.fail("Ошибка перевода");
            case DONE -> throw new IllegalArgumentException(
                    "Для этого теста статус DONE недопустим"
            );
        }

        return job;
    }

    private TranslationJob newJob(FileFormat format) {
        TranslationJob job = new TranslationJob(
                new User(
                        "user@example.com",
                        "encoded-password"
                ),
                "uploads/user-id/source." + format.jsonValue(),
                "results/user-id/result." + format.jsonValue(),
                "en",
                "ru",
                format
        );
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }
}
