package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentFormatValidator {

    FileFormat supportedFormat();

    void validate(byte[] content);

    default void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentContentException();
        }

        try {
            validate(file.getBytes());
        } catch (IOException exception) {
            throw new InvalidDocumentContentException();
        }
    }
}
