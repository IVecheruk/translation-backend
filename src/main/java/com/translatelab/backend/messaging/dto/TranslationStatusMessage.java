package com.translatelab.backend.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.entity.TranslationJob;

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
    public static final int MAX_ERROR_MESSAGE_LENGTH =
            TranslationJob.MAX_ERROR_DETAIL_LENGTH;

    public TranslationStatusMessage {
        if (errorMessage != null
                && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "error_message не должен превышать "
                            + MAX_ERROR_MESSAGE_LENGTH
                            + " символов"
            );
        }
    }
}
