package com.translatelab.backend.translation.exception;

public class InvalidDocumentContentException extends InvalidDocumentUploadException {
    public InvalidDocumentContentException() {
        super("Содержимое документа не соответствует заявленному формату");
    }
}
