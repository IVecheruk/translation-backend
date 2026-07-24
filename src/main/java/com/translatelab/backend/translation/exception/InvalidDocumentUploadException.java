package com.translatelab.backend.translation.exception;

public class InvalidDocumentUploadException extends RuntimeException {
    public InvalidDocumentUploadException(String message) {
        super(message);
    }
}
