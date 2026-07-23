package com.translatelab.backend.storage.service;

import com.translatelab.backend.config.StorageProperties;
import com.translatelab.backend.storage.exception.StorageException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Objects;

@Service
public class StorageService {

    private static final String DEFAULT_CONTENT_TYPE =
            "application/octet-stream";

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    public StorageService(
            MinioClient minioClient,
            StorageProperties storageProperties
    ) {
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
    }

    public String upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {
        validateUploadArguments(objectKey, inputStream, size);

        String actualContentType = resolveContentType(contentType);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.bucket())
                            .object(objectKey)
                            .stream(inputStream, size, -1L)
                            .contentType(actualContentType)
                            .build()
            );

            return objectKey;
        } catch (Exception exception) {
            throw new StorageException(
                    "Не удалось загрузить файл в MinIO: "
                    + objectKey, exception
            );
        }
    }

    private void validateUploadArguments(
            String objectKey,
            InputStream inputStream,
            long size
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Ключ объекта не должен быть пустым"
            );
        }

        Objects.requireNonNull(
                inputStream,
                "Поток файла не должен быть null"
        );

        if (size < 0) {
            throw new IllegalArgumentException(
                    "Размер файла не должен быть отрицательным"
            );
        }
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }

        return contentType;
    }
}