package com.translatelab.backend.translation.exception;

public class TranslationResultExpiredException extends RuntimeException {

    public TranslationResultExpiredException() {
        super("Срок хранения результата перевода истек");
    }
}
