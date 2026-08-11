package com.translatelab.backend.storage;

import com.translatelab.backend.storage.service.StorageService;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class StorageLeastPrivilegeIntegrationTest {

    @Autowired private StorageService storageService;
    @Autowired private MinioClient minioClient;

    @Test
    void shouldAllowRequiredObjectOperationsAndDenyAdministration()
            throws Exception {
        String key = "stage6-verification/" + UUID.randomUUID() + ".txt";
        byte[] expected = "least-privilege".getBytes(StandardCharsets.UTF_8);

        try {
            storageService.upload(
                    key,
                    new ByteArrayInputStream(expected),
                    expected.length,
                    "text/plain"
            );
            try (var input = storageService.download(key)) {
                assertArrayEquals(expected, input.readAllBytes());
            }

            assertThrows(
                    Exception.class,
                    () -> minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket("forbidden-" + UUID.randomUUID())
                                    .build()
                    )
            );
            assertTrue(minioClient.listBuckets().stream()
                    .allMatch(bucket -> "translation-documents".equals(
                            bucket.name()
                    )));
        } finally {
            storageService.delete(key);
        }
    }
}
