package com.translatelab.backend.messaging.exception;

public class InvalidTranslationStatusMessageException
        extends RuntimeException {

    public InvalidTranslationStatusMessageException(String message) {
        super(message);
    }
}
