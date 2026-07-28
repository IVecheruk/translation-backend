package com.translatelab.backend.user.dto;

import java.io.InputStream;
import java.util.Objects;

public record AvatarDownloadResult(

        InputStream inputStream,
        String contentType
) {
    public AvatarDownloadResult {
        Objects.requireNonNull(
                inputStream,
                "Поток аватара не должен быть null"
        );

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "Content-Type аватара не должен быть пустым"
            );
        }
    }
}
