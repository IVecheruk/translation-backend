package com.translatelab.backend.translation.dto;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentDownloadResultTest {

    @Test
    void shouldStoreDownloadData() {
        InputStream inputStream =
                new ByteArrayInputStream(new byte[]{1, 2, 3});

        DocumentDownloadResult result =
                new DocumentDownloadResult(
                        inputStream,
                        "translation.docx",
                        "application/vnd.openxmlformats-officedocument"
                                + ".wordprocessingml.document"
                );

        assertAll(
                () -> assertSame(inputStream, result.inputStream()),
                () -> assertEquals(
                        "translation.docx",
                        result.fileName()
                ),
                () -> assertEquals(
                        "application/vnd.openxmlformats-officedocument"
                                + ".wordprocessingml.document",
                        result.contentType()
                )
        );
    }

    @Test
    void shouldRejectNullInputStream() {
        assertThrows(
                NullPointerException.class,
                () -> new DocumentDownloadResult(
                        null,
                        "translation.pdf",
                        "application/pdf"
                )
        );
    }

    @Test
    void shouldRejectMissingFileName() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(null, "application/pdf")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult("", "application/pdf")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult("  ", "application/pdf")
                )
        );
    }

    @Test
    void shouldRejectMissingContentType() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult("translation.pdf", null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult("translation.pdf", "")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult("translation.pdf", "  ")
                )
        );
    }

    private DocumentDownloadResult createResult(
            String fileName,
            String contentType
    ) {
        return new DocumentDownloadResult(
                new ByteArrayInputStream(new byte[0]),
                fileName,
                contentType
        );
    }
}
