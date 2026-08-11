package com.translatelab.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(min = 20, max = 256) String token,
        @NotBlank @Size(min = 8, max = 72) String password
) {}
