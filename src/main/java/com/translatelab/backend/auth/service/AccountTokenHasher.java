package com.translatelab.backend.auth.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AccountTokenHasher {

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw new IllegalArgumentException("Некорректный одноразовый токен");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            rawToken.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
