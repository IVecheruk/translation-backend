package com.translatelab.backend.translation.validation;

import com.translatelab.backend.config.DocumentValidationProperties;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocxDocumentValidatorTest {

    @Test
    void shouldAcceptValidDocxRegardlessOfClientContentType()
            throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);

        assertDoesNotThrow(() -> validator.validate(file(validDocx())));
    }

    @Test
    void shouldRejectExecutableRenamedToDocx() {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(
                        "MZ executable".getBytes(StandardCharsets.US_ASCII)
                ))
        );
    }

    @Test
    void shouldRejectValidPdfRenamedToDocx() throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(validPdf()))
        );
    }

    @Test
    void shouldRejectEmptyZipContainer() throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(zip(Map.of())))
        );
    }

    @Test
    void shouldRejectZipWithoutRequiredOoxmlParts() throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(zip(Map.of(
                        "random.txt",
                        "not a Word document".getBytes(StandardCharsets.UTF_8)
                ))))
        );
    }

    @Test
    void shouldRejectTooManyArchiveEntries() throws IOException {
        DocxDocumentValidator validator = validator(2, 1_000_000, 2_000_000, 0.001);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(validDocx()))
        );
    }

    @Test
    void shouldRejectOversizedIndividualEntry() throws IOException {
        DocxDocumentValidator validator = validator(100, 20, 2_000_000, 0.001);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(validDocx()))
        );
    }

    @Test
    void shouldRejectExcessiveTotalUncompressedSize() throws IOException {
        DocxDocumentValidator validator = validator(100, 100, 150, 0.001);
        Map<String, byte[]> entries = minimalPackageEntries();
        entries.put("word/first.bin", new byte[80]);
        entries.put("word/second.bin", new byte[80]);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(zip(entries)))
        );
    }

    @Test
    void shouldRejectHighlyCompressedEntry() throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.5);
        byte[] repeatedContent = new byte[100_000];
        java.util.Arrays.fill(repeatedContent, (byte) 'A');

        Map<String, byte[]> entries = minimalPackageEntries();
        entries.put("word/media/repeated.bin", repeatedContent);

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(zip(entries)))
        );
    }

    @Test
    void shouldRejectNestedArchive() throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);
        Map<String, byte[]> entries = minimalPackageEntries();
        entries.put(
                "word/embeddings/archive.zip",
                zip(Map.of("payload.txt", new byte[]{1}))
        );

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(zip(entries)))
        );
    }

    @Test
    void shouldRejectPathTraversalEntry() throws IOException {
        DocxDocumentValidator validator = validator(100, 1_000_000, 2_000_000, 0.001);
        Map<String, byte[]> entries = minimalPackageEntries();
        entries.put("word/../payload.bin", new byte[]{1});

        assertThrows(
                InvalidDocumentContentException.class,
                () -> validator.validate(file(zip(entries)))
        );
    }

    private DocxDocumentValidator validator(
            int maxEntries,
            long maxEntrySize,
            long maxTotalSize,
            double minCompressionRatio
    ) {
        return new DocxDocumentValidator(new DocumentValidationProperties(
                new DocumentValidationProperties.Docx(
                        maxEntries,
                        DataSize.ofBytes(maxEntrySize),
                        DataSize.ofBytes(maxTotalSize),
                        minCompressionRatio
                )
        ));
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile(
                "file",
                "document.docx",
                "application/x-msdownload",
                content
        );
    }

    private byte[] validDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("TranslateLab");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] validPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private Map<String, byte[]> minimalPackageEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "[Content_Types].xml",
                "<Types/>".getBytes(StandardCharsets.UTF_8)
        );
        entries.put(
                "_rels/.rels",
                "<Relationships/>".getBytes(StandardCharsets.UTF_8)
        );
        entries.put(
                "word/document.xml",
                "<document/>".getBytes(StandardCharsets.UTF_8)
        );
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream archive = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                archive.putNextEntry(new ZipEntry(entry.getKey()));
                try (ByteArrayInputStream input = new ByteArrayInputStream(
                        entry.getValue()
                )) {
                    input.transferTo(archive);
                }
                archive.closeEntry();
            }
            archive.finish();
            return output.toByteArray();
        }
    }
}
