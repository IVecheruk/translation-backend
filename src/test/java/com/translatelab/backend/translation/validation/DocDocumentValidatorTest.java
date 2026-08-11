package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocDocumentValidatorTest {

    private final DocDocumentValidator validator =
            new DocDocumentValidator();

    @Test
    void shouldAcceptValidLegacyWordDocument() throws IOException {
        byte[] content;
        try (var input = getClass().getResourceAsStream(
                "/documents/valid-legacy.doc"
        )) {
            if (input == null) {
                throw new IllegalStateException("Test DOC fixture is missing");
            }
            content = input.readAllBytes();
        }

        assertDoesNotThrow(() -> validator.validate(file(content)));
    }

    @Test
    void shouldRejectExecutableRenamedToDoc() {
        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(
                        "MZ executable".getBytes(StandardCharsets.US_ASCII)
                ))
        );
    }

    @Test
    void shouldRejectArbitraryOleContainerWithoutWordStreams()
            throws IOException {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            fileSystem.createDocument(
                    new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}),
                    "NotWord"
            );
            fileSystem.writeFilesystem(output);

            assertThrows(
                    InvalidDocumentContentException.class,
                    () -> validator.validate(file(output.toByteArray()))
            );
        }
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile(
                "file",
                "document.doc",
                "application/octet-stream",
                content
        );
    }
}
