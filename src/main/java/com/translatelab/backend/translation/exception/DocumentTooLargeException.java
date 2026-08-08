package com.translatelab.backend.translation.exception;

public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException() {
        super("Размер документа превышает максимально допустимый");
    }
}
