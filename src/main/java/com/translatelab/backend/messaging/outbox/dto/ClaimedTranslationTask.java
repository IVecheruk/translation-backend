package com.translatelab.backend.messaging.outbox.dto;

import com.translatelab.backend.messaging.dto.TranslationTaskMessage;

import java.util.Objects;
import java.util.UUID;

public record ClaimedTranslationTask(
        UUID eventId,
        int attempt,
        TranslationTaskMessage message
) {
    public ClaimedTranslationTask {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(message);
        if (attempt <= 0) {
            throw new IllegalArgumentException(
                    "Номер попытки должен быть положительным"
            );
        }
    }
}
