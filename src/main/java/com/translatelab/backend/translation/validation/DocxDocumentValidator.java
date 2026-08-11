package com.translatelab.backend.translation.validation;

import com.translatelab.backend.config.DocumentValidationProperties;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocxDocumentValidator implements DocumentFormatValidator {

    private static final int BUFFER_SIZE = 8192;
    private static final String WORD_DOCUMENT_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument"
                    + ".wordprocessingml.document.main+xml";
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "[Content_Types].xml",
            "_rels/.rels",
            "word/document.xml"
    );

    private final DocumentValidationProperties properties;

    public DocxDocumentValidator(DocumentValidationProperties properties) {
        this.properties = properties;
    }

    @Override
    public FileFormat supportedFormat() {
        return FileFormat.DOCX;
    }

    @Override
    public void validate(byte[] content) {
        try {
            validateArchive(content);
            validateWordPackage(content);
        } catch (InvalidDocumentContentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidDocumentContentException();
        }
    }

    private void validateArchive(byte[] content) throws IOException {
        DocumentValidationProperties.Docx limits = properties.docx();
        Set<String> entries = new HashSet<>();
        long totalUncompressedSize = 0;
        int entryCount = 0;

        try (ZipInputStream archive = new ZipInputStream(
                new ByteArrayInputStream(content)
        )) {
            ZipEntry entry;

            while ((entry = archive.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > limits.maxEntries()) {
                    throw new InvalidDocumentContentException();
                }

                String entryName = normalizeEntryName(entry.getName());
                if (!entries.add(entryName)) {
                    throw new InvalidDocumentContentException();
                }

                if (!entry.isDirectory()) {
                    totalUncompressedSize = validateEntry(
                            archive,
                            entry,
                            totalUncompressedSize,
                            limits
                    );
                }

                archive.closeEntry();
            }
        }

        if (entryCount == 0 || !entries.containsAll(REQUIRED_ENTRIES)) {
            throw new InvalidDocumentContentException();
        }
    }

    private long validateEntry(
            ZipInputStream archive,
            ZipEntry entry,
            long totalBeforeEntry,
            DocumentValidationProperties.Docx limits
    ) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        byte[] prefix = new byte[4];
        int prefixLength = 0;
        long entrySize = 0;
        int bytesRead;

        while ((bytesRead = archive.read(buffer)) != -1) {
            if (prefixLength < prefix.length) {
                int copyLength = Math.min(
                        prefix.length - prefixLength,
                        bytesRead
                );
                System.arraycopy(
                        buffer,
                        0,
                        prefix,
                        prefixLength,
                        copyLength
                );
                prefixLength += copyLength;
            }

            entrySize += bytesRead;
            if (entrySize > limits.maxEntrySize().toBytes()
                    || totalBeforeEntry + entrySize
                    > limits.maxTotalUncompressedSize().toBytes()) {
                throw new InvalidDocumentContentException();
            }
        }

        if (isZipSignature(prefix, prefixLength)) {
            throw new InvalidDocumentContentException();
        }

        long compressedSize = entry.getCompressedSize();
        if (entrySize > 0 && compressedSize >= 0) {
            double compressionRatio = (double) compressedSize / entrySize;
            if (compressionRatio < limits.minCompressionRatio()) {
                throw new InvalidDocumentContentException();
            }
        }

        return totalBeforeEntry + entrySize;
    }

    private String normalizeEntryName(String entryName) {
        if (entryName == null
                || entryName.isBlank()
                || entryName.startsWith("/")
                || entryName.startsWith("\\")
                || entryName.contains("\\")
                || entryName.equals("..")
                || entryName.startsWith("../")
                || entryName.contains("/../")) {
            throw new InvalidDocumentContentException();
        }

        return entryName;
    }

    private boolean isZipSignature(byte[] prefix, int length) {
        return length >= 4
                && prefix[0] == 'P'
                && prefix[1] == 'K'
                && ((prefix[2] == 3 && prefix[3] == 4)
                || (prefix[2] == 5 && prefix[3] == 6)
                || (prefix[2] == 7 && prefix[3] == 8));
    }

    private void validateWordPackage(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(content)
        )) {
            if (document.getDocument() == null
                    || document.getDocument().getBody() == null
                    || !WORD_DOCUMENT_CONTENT_TYPE.equals(
                    document.getPackagePart().getContentType()
            )) {
                throw new InvalidDocumentContentException();
            }
        }
    }
}
