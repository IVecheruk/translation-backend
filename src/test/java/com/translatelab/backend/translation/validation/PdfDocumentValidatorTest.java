package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfDocumentValidatorTest {

    private final PdfDocumentValidator validator =
            new PdfDocumentValidator();

    @Test
    void shouldAcceptParseablePdfWithAtLeastOnePage() throws IOException {
        assertDoesNotThrow(() -> validator.validate(file(validPdf())));
    }

    @Test
    void shouldRejectExecutableRenamedToPdf() {
        byte[] executable = "MZ executable".getBytes(StandardCharsets.US_ASCII);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(executable))
        );
    }

    @Test
    void shouldRejectTruncatedPdfWithOnlySignature() {
        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(
                        "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
                ))
        );
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile(
                "file",
                "document.pdf",
                "application/octet-stream",
                content
        );
    }

    private byte[] validPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
