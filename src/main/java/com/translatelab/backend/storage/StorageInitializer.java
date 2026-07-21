package com.translatelab.backend.storage;

import io.minio.MinioClient;
import org.springframework.stereotype.Component;

@Component
public class StorageInitializer {

    private final MinioClient minioClient;
}