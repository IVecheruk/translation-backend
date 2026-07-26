package com.translatelab.backend.translation.exception;

public class TranslationResultNotReadyException extends RuntimeException {
    public TranslationResultNotReadyException() {
        super("Результат перевода не доступен для скачивания");
    }
}
