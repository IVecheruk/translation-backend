package com.translatelab.backend.common.web;

import com.translatelab.backend.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public class RateLimitResponseWriter {

    private final ObjectMapper objectMapper;

    public RateLimitResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            long retryAfterSeconds
    ) throws IOException {
        String correlationId = CorrelationIdContext.currentOrCreate();
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Слишком много запросов. Повторите попытку позже",
                request.getRequestURI(),
                Map.of(),
                correlationId
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
