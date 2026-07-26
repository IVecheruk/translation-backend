package com.translatelab.backend.translation.dto;

import java.io.InputStream;
import java.util.Objects;

public record DocumentDownloadResult(

        InputStream inputStream, // Содержимое файла из MinIO

        String fileName, // имя, которое увидит при скачивании

        String contentType // MIME-тип файла для HTTP-заголовка
) {
    public DocumentDownloadResult {
        Objects.requireNonNull(
                inputStream,
                "Поток скачиваемого файла не должен быть null"
        );

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Имя скачиваемого файла не должно быть пустым"
            );
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "Content-Type скачиваемого файла не должен быть пустым"
            );
        }
    }
}