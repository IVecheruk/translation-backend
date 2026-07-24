package com.translatelab.backend.translation.service;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.UnsupportedFileFormatException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FileFormatResolver {

    public FileFormat resolve(String originalFileName) {

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new UnsupportedFileFormatException();
        }

        String filename = originalFileName.strip();
        int lastDotIndex = filename.lastIndexOf('.');

        if (lastDotIndex <= 0 || lastDotIndex == filename.length() - 1) {
            throw new UnsupportedFileFormatException();
        }

        String extension = filename
                .substring(lastDotIndex + 1)
                .toLowerCase(Locale.ROOT);

        return switch (extension) {
            case "docx" -> FileFormat.DOCX;
            case "doc" -> FileFormat.DOC;
            case "pdf" -> FileFormat.PDF;
            default -> throw new UnsupportedFileFormatException();
        };
    }
}