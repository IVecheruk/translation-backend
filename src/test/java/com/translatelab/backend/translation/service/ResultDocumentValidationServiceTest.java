package com.translatelab.backend.translation.service;

import com.translatelab.backend.config.DocumentUploadProperties;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import com.translatelab.backend.translation.validation.DocumentContentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ResultDocumentValidationServiceTest {

    private static final String RESULT_KEY =
            "results/user-id/result-id.docx";

    @Mock
    private StorageService storageService;

    @Mock
    private DocumentContentValidator documentContentValidator;

    private ResultDocumentValidationService service;

    @BeforeEach
    void setUp() {
        service = new ResultDocumentValidationService(
                storageService,
                documentContentValidator,
                new DocumentUploadProperties(DataSize.ofBytes(8))
        );
    }

    @Test
    void shouldDownloadAndValidateStoredResult() {
        byte[] content = {1, 2, 3, 4};
        given(storageService.download(RESULT_KEY))
                .willReturn(new ByteArrayInputStream(content));

        service.validate(RESULT_KEY, FileFormat.DOCX);

        ArgumentCaptor<byte[]> contentCaptor =
                ArgumentCaptor.forClass(byte[].class);
        verify(documentContentValidator).validate(
                contentCaptor.capture(),
                eq(FileFormat.DOCX)
        );
        assertArrayEquals(
                content,
                contentCaptor.getValue()
        );
    }

    @Test
    void shouldRejectStoredResultLargerThanConfiguredLimit() {
        given(storageService.download(RESULT_KEY))
                .willReturn(new ByteArrayInputStream(new byte[9]));

        assertThrows(
                InvalidDocumentContentException.class,
                () -> service.validate(RESULT_KEY, FileFormat.DOCX)
        );

        verifyNoInteractions(documentContentValidator);
    }

    @Test
    void shouldMapStorageStreamReadFailureToStorageException() {
        given(storageService.download(RESULT_KEY))
                .willReturn(new FailingInputStream());

        assertThrows(
                StorageException.class,
                () -> service.validate(RESULT_KEY, FileFormat.DOCX)
        );

        verifyNoInteractions(documentContentValidator);
    }

    private static final class FailingInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException("read failed");
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
                throws IOException {
            throw new IOException("read failed");
        }
    }
}
