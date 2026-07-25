package com.translatelab.backend.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.translation.entity.TranslationStatus;

import java.util.UUID;

public record DocumentStatusResponse(

        @JsonProperty("job_id")
        UUID jobId,

        TranslationStatus status,

        int progress,

        @JsonProperty("error_message")
        String errorMessage
) {
}
