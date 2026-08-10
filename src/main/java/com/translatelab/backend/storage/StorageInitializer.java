package com.translatelab.backend.storage;

import com.translatelab.backend.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StorageInitializer implements ApplicationRunner {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    public StorageInitializer(
            MinioClient minioClient,
            StorageProperties storageProperties
    ) {
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = storageProperties.bucket();

        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!bucketExists) {
                throw new IllegalStateException(
                        "Настроенный bucket MinIO не существует"
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Не удалось проверить доступ к bucket MinIO",
                    exception
            );
        }
    }
}
