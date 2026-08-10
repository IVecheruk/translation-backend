package com.translatelab.backend.common.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp, // Время ошибки
        int status, // HTTP - код
        String message, // Понятное описание
        String path, // Адрес запроса
        Map<String, String> fieldErrors, // Ошибки отдельных полей DTO
        @JsonProperty("correlation_id") String correlationId
) {

    public ApiError(
            Instant timestamp,
            int status,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        this(timestamp, status, message, path, fieldErrors, null);
    }
}
