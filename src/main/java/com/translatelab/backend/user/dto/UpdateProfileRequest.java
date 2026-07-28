package com.translatelab.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,29}$")
        String username,

        @JsonProperty("display_name")
        @Size(max = 80)
        String displayName,

        @Size(max = 50)
        String nickname,

        @Size(max = 100)
        String profession,

        @Size(max = 1000)
        String bio
) {
    public UpdateProfileRequest {
        username = normalizeOptional(username);
        displayName = normalizeOptional(displayName);
        nickname = normalizeOptional(nickname);
        profession = normalizeOptional(profession);
        bio = normalizeOptional(bio);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }
}
