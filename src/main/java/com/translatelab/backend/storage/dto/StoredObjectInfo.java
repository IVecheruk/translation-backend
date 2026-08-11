package com.translatelab.backend.storage.dto;

import java.time.Instant;

public record StoredObjectInfo(
        String objectKey,
        Instant lastModified
) {

    public StoredObjectInfo {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Ключ объекта не должен быть пустым"
            );
        }

        if (lastModified == null) {
            throw new IllegalArgumentException(
                    "Время изменения объекта не должно быть null"
            );
        }
    }
}
