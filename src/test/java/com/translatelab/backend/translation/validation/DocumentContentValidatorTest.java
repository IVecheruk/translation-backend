package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.entity.FileFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentContentValidatorTest {

    @Test
    void shouldDelegateToValidatorForResolvedFormat() {
        DocumentFormatValidator pdf = validator(FileFormat.PDF);
        DocumentFormatValidator doc = validator(FileFormat.DOC);
        DocumentFormatValidator docx = validator(FileFormat.DOCX);
        DocumentContentValidator coordinator = new DocumentContentValidator(
                List.of(pdf, doc, docx)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                null,
                new byte[]{1}
        );

        coordinator.validate(file, FileFormat.PDF);

        verify(pdf).validate(file);
    }

    @Test
    void shouldRejectDuplicateFormatValidatorsAtStartup() {
        assertThrows(
                IllegalStateException.class,
                () -> new DocumentContentValidator(List.of(
                        validator(FileFormat.PDF),
                        validator(FileFormat.PDF),
                        validator(FileFormat.DOC),
                        validator(FileFormat.DOCX)
                ))
        );
    }

    @Test
    void shouldRejectMissingFormatValidatorAtStartup() {
        assertThrows(
                IllegalStateException.class,
                () -> new DocumentContentValidator(List.of(
                        validator(FileFormat.PDF),
                        validator(FileFormat.DOCX)
                ))
        );
    }

    private DocumentFormatValidator validator(FileFormat format) {
        DocumentFormatValidator validator = mock(DocumentFormatValidator.class);
        when(validator.supportedFormat()).thenReturn(format);
        return validator;
    }
}
