package com.translatelab.backend.common.exception;

public class RequestRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RequestRateLimitExceededException(long retryAfterSeconds) {
        super("Слишком много запросов. Повторите попытку позже");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
