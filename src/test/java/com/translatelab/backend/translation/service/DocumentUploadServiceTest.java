package com.translatelab.backend.translation.service;

import com.translatelab.backend.config.DocumentUploadProperties;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.service.StorageKeyGenerator;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.dto.DocumentUploadResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.exception.InvalidDocumentUploadException;
import com.translatelab.backend.translation.validation.DocumentContentValidator;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "d795a84a-a8e9-4075-9c6a-7745f45fdcc7"
    );
    private static final UUID JOB_ID = UUID.fromString(
            "dba94f7a-edc6-4ab5-9f41-46817472cf73"
    );
    private static final String SOURCE_KEY =
            "uploads/user-id/source.docx";
    private static final String RESULT_KEY =
            "results/user-id/result.docx";

    @Mock
    private UserRepository userRepository;
    @Mock
    private FileFormatResolver fileFormatResolver;
    @Mock
    private StorageKeyGenerator storageKeyGenerator;
    @Mock
    private StorageService storageService;
    @Mock
    private TranslationJobCreationService jobCreationService;
    @Mock
    private DocumentContentValidator documentContentValidator;

    private DocumentUploadService service;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = new DocumentUploadService(
                userRepository,
                fileFormatResolver,
                storageKeyGenerator,
                storageService,
                jobCreationService,
                new DocumentUploadProperties(DataSize.ofMegabytes(10)),
                documentContentValidator
        );
        file = new MockMultipartFile(
                "file",
                "document.docx",
                FileFormat.DOCX.contentType(),
                "document-content".getBytes()
        );
        lenient().when(fileFormatResolver.resolve("document.docx"))
                .thenReturn(FileFormat.DOCX);
        lenient().when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(mock(User.class)));
        lenient().when(storageKeyGenerator.generateSourceFileKey(
                USER_ID,
                FileFormat.DOCX
        )).thenReturn(SOURCE_KEY);
        lenient().when(storageKeyGenerator.generateResultFileKey(
                USER_ID,
                FileFormat.DOCX
        )).thenReturn(RESULT_KEY);
    }

    @Test
    void shouldUploadFileAndAtomicallyCreateJobAndOutbox() {
        TranslationJob job = mock(TranslationJob.class);
        given(job.getId()).willReturn(JOB_ID);
        given(jobCreationService.create(
                USER_ID,
                SOURCE_KEY,
                RESULT_KEY,
                "en",
                "ru",
                FileFormat.DOCX
        )).willReturn(job);

        DocumentUploadResponse response = service.upload(
                USER_ID,
                file,
                " EN ",
                " RU "
        );

        assertEquals(JOB_ID, response.jobId());
        verify(documentContentValidator).validate(file, FileFormat.DOCX);
        verify(storageService).upload(
                eq(SOURCE_KEY),
                any(InputStream.class),
                eq(file.getSize()),
                eq(FileFormat.DOCX.contentType())
        );
        verify(jobCreationService).create(
                USER_ID,
                SOURCE_KEY,
                RESULT_KEY,
                "en",
                "ru",
                FileFormat.DOCX
        );
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void shouldRejectEqualLanguagesBeforeStorage() {
        assertThrows(
                InvalidDocumentUploadException.class,
                () -> service.upload(USER_ID, file, "EN", "en")
        );

        verifyNoInteractions(storageService, jobCreationService);
    }

    @Test
    void shouldStopWhenDocumentContentIsInvalid() {
        InvalidDocumentUploadException failure =
                new InvalidDocumentUploadException("invalid content");
        doThrow(failure).when(documentContentValidator)
                .validate(file, FileFormat.DOCX);

        InvalidDocumentUploadException actual = assertThrows(
                InvalidDocumentUploadException.class,
                () -> service.upload(USER_ID, file, "en", "ru")
        );

        assertSame(failure, actual);
        verifyNoInteractions(storageService, jobCreationService);
    }

    @Test
    void shouldStopWhenUserDoesNotExist() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> service.upload(USER_ID, file, "en", "ru")
        );

        verifyNoInteractions(storageService, jobCreationService);
    }

    @Test
    void shouldDeleteObjectWhenDatabaseTransactionFails() {
        RuntimeException failure = new RuntimeException("database failure");
        given(jobCreationService.create(
                USER_ID,
                SOURCE_KEY,
                RESULT_KEY,
                "en",
                "ru",
                FileFormat.DOCX
        )).willThrow(failure);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.upload(USER_ID, file, "en", "ru")
        );

        assertSame(failure, actual);
        verify(storageService).delete(SOURCE_KEY);
    }

    @Test
    void shouldNotCreateDatabaseStateWhenStorageUploadFails() {
        StorageException failure = new StorageException(
                "MinIO timeout",
                new java.net.SocketTimeoutException("timed out")
        );
        doThrow(failure).when(storageService).upload(
                eq(SOURCE_KEY),
                any(InputStream.class),
                eq(file.getSize()),
                eq(FileFormat.DOCX.contentType())
        );

        StorageException actual = assertThrows(
                StorageException.class,
                () -> service.upload(USER_ID, file, "en", "ru")
        );

        assertSame(failure, actual);
        verifyNoInteractions(jobCreationService);
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void shouldPreserveCleanupFailureAsSuppressed() {
        RuntimeException creationFailure =
                new RuntimeException("database failure");
        RuntimeException cleanupFailure =
                new RuntimeException("storage failure");
        given(jobCreationService.create(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).willThrow(creationFailure);
        doThrow(cleanupFailure).when(storageService).delete(SOURCE_KEY);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.upload(USER_ID, file, "en", "ru")
        );

        assertSame(creationFailure, actual);
        assertSame(cleanupFailure, actual.getSuppressed()[0]);
    }

    @Test
    void shouldDeleteObjectWhenInputStreamCloseFailsAfterUpload()
            throws Exception {
        MultipartFile brokenFile = mock(MultipartFile.class);
        InputStream inputStream = new ByteArrayInputStream(new byte[]{1}) {
            @Override
            public void close() throws IOException {
                throw new IOException("close failure");
            }
        };
        given(brokenFile.isEmpty()).willReturn(false);
        given(brokenFile.getSize()).willReturn(1L);
        given(brokenFile.getOriginalFilename()).willReturn("document.docx");
        given(brokenFile.getInputStream()).willReturn(inputStream);

        assertThrows(
                InvalidDocumentUploadException.class,
                () -> service.upload(USER_ID, brokenFile, "en", "ru")
        );

        verify(storageService).upload(
                eq(SOURCE_KEY),
                eq(inputStream),
                eq(1L),
                eq(FileFormat.DOCX.contentType())
        );
        verify(storageService).delete(SOURCE_KEY);
        verify(jobCreationService, never()).create(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }
}
