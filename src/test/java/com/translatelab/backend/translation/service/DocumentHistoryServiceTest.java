package com.translatelab.backend.translation.service;

import com.translatelab.backend.translation.dto.DocumentHistoryItemResponse;
import com.translatelab.backend.translation.dto.DocumentHistoryResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.entity.TranslationErrorCode;
import com.translatelab.backend.translation.exception.InvalidPaginationException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentHistoryServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "584175c1-d670-4ef5-91e4-896ffb80b9cc"
    );
    private static final UUID FIRST_JOB_ID = UUID.fromString(
            "10cf4338-5af4-47c0-b322-c17a283a9674"
    );
    private static final UUID SECOND_JOB_ID = UUID.fromString(
            "0a992ad3-8251-4e18-b6a8-29d783b11242"
    );

    @Mock
    private TranslationJobRepository translationJobRepository;

    private DocumentHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DocumentHistoryService(
                translationJobRepository
        );
    }

    @Test
    void shouldReturnMappedHistoryPage() {
        TranslationJob completedJob = createJob(
                FIRST_JOB_ID,
                FileFormat.DOCX,
                TranslationStatus.DONE,
                Instant.parse("2026-07-26T08:00:00Z"),
                Instant.parse("2026-07-26T08:05:00Z")
        );
        TranslationJob failedJob = createJob(
                SECOND_JOB_ID,
                FileFormat.PDF,
                TranslationStatus.FAILED,
                Instant.parse("2026-07-26T07:00:00Z"),
                Instant.parse("2026-07-26T07:03:00Z")
        );
        PageRequest pageable = PageRequest.of(1, 2);
        Page<TranslationJob> jobsPage = new PageImpl<>(
                List.of(completedJob, failedJob),
                pageable,
                5
        );
        given(translationJobRepository
                .findAllByUser_IdOrderByCreatedAtDesc(
                        USER_ID,
                        pageable
                ))
                .willReturn(jobsPage);

        DocumentHistoryResponse response =
                service.getHistory(USER_ID, 1, 2);

        assertAll(
                () -> assertEquals(1, response.page()),
                () -> assertEquals(2, response.size()),
                () -> assertEquals(5, response.totalElements()),
                () -> assertEquals(3, response.totalPages()),
                () -> assertEquals(false, response.first()),
                () -> assertEquals(false, response.last()),
                () -> assertEquals(2, response.items().size())
        );

        DocumentHistoryItemResponse firstItem =
                response.items().getFirst();
        DocumentHistoryItemResponse secondItem =
                response.items().get(1);

        assertAll(
                () -> assertEquals(
                        FIRST_JOB_ID,
                        firstItem.jobId()
                ),
                () -> assertEquals("en", firstItem.sourceLang()),
                () -> assertEquals("ru", firstItem.targetLang()),
                () -> assertEquals(
                        FileFormat.DOCX,
                        firstItem.format()
                ),
                () -> assertEquals(
                        TranslationStatus.DONE,
                        firstItem.status()
                ),
                () -> assertEquals(100, firstItem.progress()),
                () -> assertEquals(
                        SECOND_JOB_ID,
                        secondItem.jobId()
                ),
                () -> assertEquals(
                        TranslationStatus.FAILED,
                        secondItem.status()
                ),
                () -> assertEquals(47, secondItem.progress()),
                () -> assertEquals(
                        "Не удалось перевести документ",
                        secondItem.errorMessage()
                ),
                () -> assertEquals(
                        TranslationErrorCode.TRANSLATION_FAILED,
                        secondItem.errorCode()
                )
        );

        verify(translationJobRepository)
                .findAllByUser_IdOrderByCreatedAtDesc(
                        USER_ID,
                        pageable
                );
    }

    @Test
    void shouldReturnEmptyHistoryPage() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(translationJobRepository
                .findAllByUser_IdOrderByCreatedAtDesc(
                        USER_ID,
                        pageable
                ))
                .willReturn(Page.empty(pageable));

        DocumentHistoryResponse response =
                service.getHistory(USER_ID, 0, 20);

        assertAll(
                () -> assertEquals(List.of(), response.items()),
                () -> assertEquals(0, response.totalElements()),
                () -> assertEquals(0, response.totalPages()),
                () -> assertEquals(true, response.first()),
                () -> assertEquals(true, response.last())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, Номер страницы не должен быть отрицательным",
            "0, 0, Размер страницы должен быть положительным",
            "0, 101, Размер страницы не должен превышать 100"
    })
    void shouldRejectInvalidPagination(
            int page,
            int size,
            String expectedMessage
    ) {
        InvalidPaginationException exception = assertThrows(
                InvalidPaginationException.class,
                () -> service.getHistory(USER_ID, page, size)
        );

        assertEquals(expectedMessage, exception.getMessage());
        verifyNoInteractions(translationJobRepository);
    }

    @Test
    void shouldRejectNullUserIdBeforeCallingRepository() {
        assertThrows(
                NullPointerException.class,
                () -> service.getHistory(null, 0, 20)
        );

        verifyNoInteractions(translationJobRepository);
    }

    private TranslationJob createJob(
            UUID jobId,
            FileFormat format,
            TranslationStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
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
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "createdAt", createdAt);
        ReflectionTestUtils.setField(job, "updatedAt", updatedAt);

        switch (status) {
            case PENDING -> {
            }
            case PROCESSING -> job.startProcessing();
            case DONE -> {
                job.startProcessing();
                job.complete();
            }
            case FAILED -> {
                job.startProcessing();
                job.updateProgress(47);
                job.fail("Ошибка обработки документа");
            }
        }

        return job;
    }
}
