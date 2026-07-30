package com.translatelab.backend.plan.exception;

public class FeatureNotAvailableException extends RuntimeException {
    public FeatureNotAvailableException() {
        super("Функция недоступна в текущем тарифе");
    }
}
