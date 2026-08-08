package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class PdfDocumentValidator implements DocumentFormatValidator {

    private static final byte[] PDF_SIGNATURE =
            "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Override
    public FileFormat supportedFormat() {
        return FileFormat.PDF;
    }

    @Override
    public void validate(byte[] contentBytes) {
        try (
                InputStream inputStream = new java.io.ByteArrayInputStream(
                        contentBytes
                );
                RandomAccessReadBuffer content =
                        new RandomAccessReadBuffer(inputStream)
        ) {
            validateSignature(content);
            content.seek(0);

            try (PDDocument document = Loader.loadPDF(content)) {
                if (document.getNumberOfPages() < 1) {
                    throw new InvalidDocumentContentException();
                }
            }
        } catch (IOException exception) {
            throw new InvalidDocumentContentException();
        }
    }

    private void validateSignature(
            RandomAccessReadBuffer content
    ) throws IOException {
        byte[] actualSignature = new byte[PDF_SIGNATURE.length];

        int bytesRead = content.read(
                actualSignature,
                0,
                actualSignature.length
        );

        if (bytesRead != PDF_SIGNATURE.length
                || !Arrays.equals(actualSignature, PDF_SIGNATURE)) {
            throw new InvalidDocumentContentException();
        }
    }
}
