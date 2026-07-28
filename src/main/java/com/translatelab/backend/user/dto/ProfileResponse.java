package com.translatelab.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(

        @JsonProperty("user_id")
        UUID userId,

        String email,

        String username,

        @JsonProperty("display_name")
        String displayName,

        String nickname,

        String profession,

        String bio,

        @JsonProperty("has_avatar")
        boolean hasAvatar,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("updated_at")
        Instant updatedAt
) {
}
