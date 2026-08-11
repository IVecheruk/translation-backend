package com.translatelab.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountTokenRequest(
        @NotBlank @Size(min = 20, max = 256) String token
) {}
