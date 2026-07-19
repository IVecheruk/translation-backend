package com.translatelab.backend.common.exception;


import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp, // Время ошибки
        int status, // HTTP - код
        String message, // Понятное описание
        String path, // Адрес запроса
        Map<String, String> fieldErrors // Ошибки отдельный полей DTO
) {
}
