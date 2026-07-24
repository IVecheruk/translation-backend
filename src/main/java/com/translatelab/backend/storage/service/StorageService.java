package com.translatelab.backend.storage.service;

import com.translatelab.backend.config.StorageProperties;
import com.translatelab.backend.storage.exception.StorageException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
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

    public void delete(String objectKey) {
        validateObjectKey(objectKey);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(storageProperties.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageException(
                    "Не удалось удалить файл из MinIO: "
                            + objectKey, exception
            );
        }
    }

    private void validateUploadArguments(
            String objectKey,
            InputStream inputStream,
            long size
    ) {
        validateObjectKey(objectKey);

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

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Ключ объекта не должен быть пустым"
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
