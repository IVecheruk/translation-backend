package com.translatelab.backend.translation.service;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.UnsupportedFileFormatException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileFormatResolverTest {

    private final FileFormatResolver resolver =
            new FileFormatResolver();

    @ParameterizedTest
    @MethodSource("supportedFilenames")
    void shouldResolveSupportedFileFormat(
            String filename,
            FileFormat expectedFormat
    ) {
        assertEquals(expectedFormat, resolver.resolve(filename));
    }

    @ParameterizedTest
    @MethodSource("unsupportedFilenames")
    void shouldRejectUnsupportedFilename(String filename) {
        UnsupportedFileFormatException exception = assertThrows(
                UnsupportedFileFormatException.class,
                () -> resolver.resolve(filename)
        );

        assertEquals(
                "Поддерживаются только файлы форматов DOCX, DOC и PDF",
                exception.getMessage()
        );
    }

    private static Stream<Arguments> supportedFilenames() {
        return Stream.of(
                Arguments.of("document.docx", FileFormat.DOCX),
                Arguments.of("document.doc", FileFormat.DOC),
                Arguments.of("document.pdf", FileFormat.PDF),
                Arguments.of("DOCUMENT.PDF", FileFormat.PDF),
                Arguments.of("report.final.DoCx", FileFormat.DOCX),
                Arguments.of("  document.pdf  ", FileFormat.PDF)
        );
    }

    private static Stream<Arguments> unsupportedFilenames() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("document"),
                Arguments.of("document."),
                Arguments.of(".pdf"),
                Arguments.of("document.txt"),
                Arguments.of("document.pdf.exe")
        );
    }
}
