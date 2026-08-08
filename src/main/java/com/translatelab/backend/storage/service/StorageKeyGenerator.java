package com.translatelab.backend.storage.service;

import com.translatelab.backend.translation.entity.FileFormat;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class StorageKeyGenerator {

    private static final String SOURCE_FILES_PREFIX = "uploads";
    private static final String RESULT_FILES_PREFIX = "results";

    public String generateSourceFileKey(
            UUID userId,
            FileFormat fileFormat
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );
        Objects.requireNonNull(
                fileFormat,
                "Формат файла не должен быть null"
        );

        String extension = fileFormat
                .name()
                .toLowerCase(Locale.ROOT);

        UUID objectId = UUID.randomUUID();

        return SOURCE_FILES_PREFIX
                + "/"
                + userId
                +"/"
                + objectId
                + "."
                + extension;
    }

    public String generateResultFileKey(
            UUID userId,
            FileFormat fileFormat
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );
        Objects.requireNonNull(
                fileFormat,
                "Формат файла не должен быть null"
        );

        String extension = fileFormat
                .name()
                .toLowerCase(Locale.ROOT);

        return RESULT_FILES_PREFIX
                + "/"
                + userId
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;
    }
}
