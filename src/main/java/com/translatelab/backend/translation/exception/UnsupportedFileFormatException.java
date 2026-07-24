package com.translatelab.backend.translation.exception;

public class UnsupportedFileFormatException extends RuntimeException {
    public UnsupportedFileFormatException() {
        super("Поддерживаются только файлы форматов DOCX, DOC и PDF");
    }
}
