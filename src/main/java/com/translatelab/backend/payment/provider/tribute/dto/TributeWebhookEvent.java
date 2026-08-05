package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeWebhookEvent(

        String name,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("sent_at")
        Instant sentAt,

        JsonNode payload
) {

    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    public TributeWebhookEvent {
        name = normalizeName(name);
        validateTimestamps(createdAt, sentAt);
        validatePayload(payload);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Название события Tribute не должно быть null");
        }

        String normalized = name.strip();

        if (!EVENT_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Некорректный формат названия события Tribute");
        }

        return normalized;
    }

    private static void validateTimestamps(Instant createdAt, Instant sentAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("Время создания события Tribute не должно быть null");
        }

        if (sentAt == null) {
            throw new IllegalArgumentException("Время отправки события Tribute не должно быть null");
        }

        if (sentAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Событие Tribute не может быть отправлено " +
                    "раньше времени его создания");
        }
    }

    private static void validatePayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Payload события Tribute не должен быть null");
        }

        if (!payload.isObject()) {
            throw new IllegalArgumentException("Payload события Tribute должен быть JSON-объектом");
        }
    }

}