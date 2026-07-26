package com.translatelab.backend.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.translation.entity.TranslationStatus;

import java.util.UUID;

public record TranslationStatusMessage(

        @JsonProperty("job_id")
        UUID jobId,

        TranslationStatus status,

        int progress,

        @JsonProperty("result_file_key")
        String resultFileKey,

        @JsonProperty("error_message")
        String errorMessage
) {
}
