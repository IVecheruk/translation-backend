package com.translatelab.backend.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.translation.entity.FileFormat;

import java.util.UUID;

public record TranslationTaskMessage(

        @JsonProperty("job_id")
        UUID jobId,

        @JsonProperty("file_key")
        String fileKey,

        @JsonProperty("result_file_key")
        String resultFileKey,

        @JsonProperty("source_lang")
        String sourceLang,

        @JsonProperty("target_lang")
        String targetLang,

        FileFormat format
) {
}
