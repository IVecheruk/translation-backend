package com.translatelab.backend.usage.exception;

public class UsageLimitExceededException extends RuntimeException {
    public UsageLimitExceededException() {
        super("Лимит использования функции на текущий период исчерпан");
    }
}
