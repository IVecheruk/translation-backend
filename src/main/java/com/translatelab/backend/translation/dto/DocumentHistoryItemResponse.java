package com.translatelab.backend.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.entity.TranslationErrorCode;

import java.time.Instant;
import java.util.UUID;

public record DocumentHistoryItemResponse(

        @JsonProperty("job_id")
        UUID jobId,

        @JsonProperty("source_lang")
        String sourceLang,

        @JsonProperty("target_lang")
        String targetLang,

        FileFormat format,

        TranslationStatus status,

        int progress,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("updated_at")
        Instant updatedAt,

        @JsonProperty("error_code")
        TranslationErrorCode errorCode,

        @JsonProperty("error_message")
        String errorMessage
) {
}
