package com.translatelab.backend.storage;

import com.translatelab.backend.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageInitializerTest {

    private static final String BUCKET = "translation-documents";

    @Mock
    private MinioClient minioClient;

    private StorageInitializer storageInitializer;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties(
                URI.create("http://localhost:9000"),
                "test-access-key",
                "test-secret-key",
                BUCKET
        );

        storageInitializer = new StorageInitializer(
                minioClient,
                storageProperties
        );
    }

    @Test
    void shouldNotCreateBucketWhenItAlreadyExists() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .willReturn(true);

        storageInitializer.run(null);

        ArgumentCaptor<BucketExistsArgs> bucketExistsArgsCaptor =
                ArgumentCaptor.forClass(BucketExistsArgs.class);

        verify(minioClient).bucketExists(
                bucketExistsArgsCaptor.capture()
        );
        assertEquals(
                BUCKET,
                bucketExistsArgsCaptor.getValue().bucket()
        );
    }

    @Test
    void shouldFailWhenBucketDoesNotExist() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .willReturn(false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> storageInitializer.run(null)
        );

        assertEquals(
                "Не удалось проверить доступ к bucket MinIO",
                exception.getMessage()
        );
    }

    @Test
    void shouldWrapMinioExceptionWhenBucketCannotBePrepared()
            throws Exception {
        RuntimeException cause = new RuntimeException(
                "MinIO is unavailable"
        );
        given(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .willThrow(cause);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> storageInitializer.run(null)
        );

        assertEquals(
                "Не удалось проверить доступ к bucket MinIO",
                exception.getMessage()
        );
        assertSame(cause, exception.getCause());
    }
}
