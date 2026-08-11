package com.translatelab.backend.storage.health;

import com.translatelab.backend.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MinioHealthIndicatorTest {

    @Mock
    private MinioClient minioClient;

    private MinioHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new MinioHealthIndicator(
                minioClient,
                new StorageProperties(
                        URI.create("http://localhost:9000"),
                        "access-key",
                        "secret-key",
                        "translation-documents"
                )
        );
    }

    @Test
    void shouldReportUpWhenConfiguredBucketExists() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .willReturn(true);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void shouldReportDownWithoutLeakingDetailsWhenBucketIsUnavailable()
            throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .willThrow(new IllegalStateException("private endpoint"));

        var health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(0, health.getDetails().size());
    }
}
