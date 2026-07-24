package com.translatelab.backend.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DocumentUploadResponse(
        @JsonProperty("job_id")
        UUID jobId
) {
}