package com.translatelab.backend.translation.entity;

public enum TranslationErrorCode {

    TRANSLATION_FAILED("Не удалось перевести документ");

    private final String publicMessage;

    TranslationErrorCode(String publicMessage) {
        this.publicMessage = publicMessage;
    }

    public String publicMessage() {
        return publicMessage;
    }
}
