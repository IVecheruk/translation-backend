package com.translatelab.backend.storage.health;

import com.translatelab.backend.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    public MinioHealthIndicator(
            MinioClient minioClient,
            StorageProperties storageProperties
    ) {
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
    }

    @Override
    public Health health() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(storageProperties.bucket())
                            .build()
            );
            return bucketExists
                    ? Health.up().build()
                    : Health.down().build();
        } catch (Exception exception) {
            return Health.down().build();
        }
    }
}
