package com.translatelab.backend.auth.dto;

public record LoginResponse(

        String accessToken, // Сам JWT
        String tokenType, // Bearer
        long expiresIn // Срок действия токена в секундах
) {
}
