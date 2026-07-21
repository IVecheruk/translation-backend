package com.translatelab.backend.storage;

import com.translatelab.backend.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
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
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Не удалось подготовить bucket MinIO: " + bucket,
                    exception
            );
        }
    }
}