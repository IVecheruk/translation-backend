package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.apache.poi.hwpf.HWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Component
public class DocDocumentValidator implements DocumentFormatValidator {

    private static final byte[] OLE_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    @Override
    public FileFormat supportedFormat() {
        return FileFormat.DOC;
    }

    @Override
    public void validate(byte[] content) {
        validateSignature(content);

        try (InputStream inputStream = new java.io.ByteArrayInputStream(content);
             HWPFDocument document = new HWPFDocument(inputStream)) {
            if (document.getRange() == null) {
                throw new InvalidDocumentContentException();
            }
        } catch (IOException | RuntimeException exception) {
            throw new InvalidDocumentContentException();
        }
    }

    private void validateSignature(byte[] content) {
        if (content.length < OLE_SIGNATURE.length
                || !Arrays.equals(
                Arrays.copyOf(content, OLE_SIGNATURE.length),
                OLE_SIGNATURE
        )) {
            throw new InvalidDocumentContentException();
        }
    }
}
