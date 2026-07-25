package com.translatelab.backend.translation.exception;

public class TranslationJobNotFoundException extends RuntimeException {
    public TranslationJobNotFoundException() {
        super("Задание перевода не найдено");
    }
}
