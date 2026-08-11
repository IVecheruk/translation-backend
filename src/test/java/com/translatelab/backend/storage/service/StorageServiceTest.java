package com.translatelab.backend.storage.service;

import com.translatelab.backend.config.StorageProperties;
import com.translatelab.backend.storage.exception.StorageException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    private static final String BUCKET = "translation-documents";
    private static final String OBJECT_KEY =
            "uploads/user-id/document-id.docx";

    @Mock
    private MinioClient minioClient;

    @Mock
    private GetObjectResponse getObjectResponse;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties(
                URI.create("http://localhost:9000"),
                "test-access-key",
                "test-secret-key",
                BUCKET
        );

        storageService = new StorageService(
                minioClient,
                storageProperties,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
    }

    @Test
    void shouldUploadObjectAndReturnItsKey() throws Exception {
        byte[] content = "test document".getBytes(
                StandardCharsets.UTF_8
        );
        InputStream inputStream = new ByteArrayInputStream(content);

        String result = storageService.upload(
                OBJECT_KEY,
                inputStream,
                content.length,
                "application/vnd.openxmlformats-officedocument"
        );

        ArgumentCaptor<PutObjectArgs> argsCaptor =
                ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(argsCaptor.capture());

        PutObjectArgs args = argsCaptor.getValue();

        assertAll(
                () -> assertEquals(OBJECT_KEY, result),
                () -> assertEquals(BUCKET, args.bucket()),
                () -> assertEquals(OBJECT_KEY, args.object()),
                () -> assertSame(inputStream, args.stream()),
                () -> assertEquals(
                        Long.valueOf(content.length),
                        args.objectSize()
                ),
                () -> assertEquals(
                        "application/vnd.openxmlformats-officedocument",
                        args.contentType().toString()
                )
        );
    }

    @Test
    void shouldUseDefaultContentTypeWhenItIsBlank() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        storageService.upload(
                OBJECT_KEY,
                inputStream,
                0,
                "  "
        );

        ArgumentCaptor<PutObjectArgs> argsCaptor =
                ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(argsCaptor.capture());

        assertEquals(
                "application/octet-stream",
                argsCaptor.getValue().contentType().toString()
        );
    }

    @Test
    void shouldWrapMinioExceptionWhenUploadFails() throws Exception {
        RuntimeException cause = new RuntimeException(
                "MinIO is unavailable"
        );
        given(minioClient.putObject(any(PutObjectArgs.class)))
                .willThrow(cause);

        StorageException exception = assertThrows(
                StorageException.class,
                () -> storageService.upload(
                        OBJECT_KEY,
                        new ByteArrayInputStream(new byte[]{1}),
                        1,
                        "application/pdf"
                )
        );

        assertEquals(
                "Не удалось загрузить файл в MinIO: " + OBJECT_KEY,
                exception.getMessage()
        );
        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldMapMinioUploadTimeoutToStorageException() throws Exception {
        UncheckedIOException cause = new UncheckedIOException(
                new SocketTimeoutException("MinIO upload timed out")
        );
        given(minioClient.putObject(any(PutObjectArgs.class)))
                .willThrow(cause);

        StorageException exception = assertThrows(
                StorageException.class,
                () -> storageService.upload(
                        OBJECT_KEY,
                        new ByteArrayInputStream(new byte[]{1}),
                        1,
                        "application/pdf"
                )
        );

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldRejectMissingObjectKeyBeforeCallingMinio() {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.upload(
                                null,
                                inputStream,
                                0,
                                "application/pdf"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.upload(
                                "",
                                inputStream,
                                0,
                                "application/pdf"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.upload(
                                "  ",
                                inputStream,
                                0,
                                "application/pdf"
                        )
                )
        );

        verifyNoInteractions(minioClient);
    }

    @Test
    void shouldRejectNullInputStreamBeforeCallingMinio() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> storageService.upload(
                        OBJECT_KEY,
                        null,
                        1,
                        "application/pdf"
                )
        );

        assertEquals(
                "Поток файла не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(minioClient);
    }

    @Test
    void shouldRejectNegativeSizeBeforeCallingMinio() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.upload(
                        OBJECT_KEY,
                        new ByteArrayInputStream(new byte[0]),
                        -1,
                        "application/pdf"
                )
        );

        assertEquals(
                "Размер файла не должен быть отрицательным",
                exception.getMessage()
        );
        verifyNoInteractions(minioClient);
    }

    @Test
    void shouldDeleteObjectFromConfiguredBucket() throws Exception {
        storageService.delete(OBJECT_KEY);

        ArgumentCaptor<RemoveObjectArgs> argsCaptor =
                ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(argsCaptor.capture());

        RemoveObjectArgs args = argsCaptor.getValue();

        assertAll(
                () -> assertEquals(BUCKET, args.bucket()),
                () -> assertEquals(OBJECT_KEY, args.object())
        );
    }

    @Test
    void shouldWrapMinioExceptionWhenDeleteFails() throws Exception {
        RuntimeException cause = new RuntimeException(
                "MinIO is unavailable"
        );
        doThrow(cause).when(minioClient).removeObject(
                any(RemoveObjectArgs.class)
        );

        StorageException exception = assertThrows(
                StorageException.class,
                () -> storageService.delete(OBJECT_KEY)
        );

        assertEquals(
                "Не удалось удалить файл из MinIO: " + OBJECT_KEY,
                exception.getMessage()
        );
        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldRejectMissingDeleteKeyBeforeCallingMinio() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.delete(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.delete("")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.delete("  ")
                )
        );

        verifyNoInteractions(minioClient);
    }

    @Test
    void shouldDownloadObjectFromConfiguredBucket() throws Exception {
        given(minioClient.getObject(any(GetObjectArgs.class)))
                .willReturn(getObjectResponse);

        InputStream result = storageService.download(OBJECT_KEY);

        ArgumentCaptor<GetObjectArgs> argsCaptor =
                ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(argsCaptor.capture());

        GetObjectArgs args = argsCaptor.getValue();

        assertAll(
                () -> assertSame(getObjectResponse, result),
                () -> assertEquals(BUCKET, args.bucket()),
                () -> assertEquals(OBJECT_KEY, args.object())
        );
    }

    @Test
    void shouldWrapMinioExceptionWhenDownloadFails()
            throws Exception {
        RuntimeException cause = new RuntimeException(
                "MinIO is unavailable"
        );
        given(minioClient.getObject(any(GetObjectArgs.class)))
                .willThrow(cause);

        StorageException exception = assertThrows(
                StorageException.class,
                () -> storageService.download(OBJECT_KEY)
        );

        assertEquals(
                "Не удалось скачать файл из MinIO: " + OBJECT_KEY,
                exception.getMessage()
        );
        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldRejectMissingDownloadKeyBeforeCallingMinio() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.download(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.download("")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.download("  ")
                )
        );

        verifyNoInteractions(minioClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldListOnlyObjectsNotNewerThanCutoffUpToLimit()
            throws Exception {
        Instant cutoff = Instant.parse("2026-08-08T06:00:00Z");
        Result<Item> oldResult = org.mockito.Mockito.mock(Result.class);
        Result<Item> newResult = org.mockito.Mockito.mock(Result.class);
        Item oldItem = org.mockito.Mockito.mock(Item.class);
        Item newItem = org.mockito.Mockito.mock(Item.class);
        given(oldResult.get()).willReturn(oldItem);
        given(newResult.get()).willReturn(newItem);
        given(oldItem.objectName()).willReturn(OBJECT_KEY);
        given(oldItem.lastModified()).willReturn(ZonedDateTime.ofInstant(
                cutoff,
                ZoneOffset.UTC
        ));
        given(newItem.lastModified()).willReturn(ZonedDateTime.ofInstant(
                cutoff.plusSeconds(1),
                ZoneOffset.UTC
        ));
        given(minioClient.listObjects(any(ListObjectsArgs.class)))
                .willReturn(List.of(oldResult, newResult));

        var result = storageService.listOlderThan(
                "uploads/",
                cutoff,
                10
        );

        ArgumentCaptor<ListObjectsArgs> argsCaptor =
                ArgumentCaptor.forClass(ListObjectsArgs.class);
        verify(minioClient).listObjects(argsCaptor.capture());
        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(OBJECT_KEY, result.getFirst().objectKey()),
                () -> assertEquals(cutoff, result.getFirst().lastModified()),
                () -> assertEquals(BUCKET, argsCaptor.getValue().bucket()),
                () -> assertEquals("uploads/", argsCaptor.getValue().prefix()),
                () -> assertEquals(true, argsCaptor.getValue().recursive())
        );
    }

    @Test
    void shouldRejectInvalidListArgumentsBeforeCallingMinio() {
        Instant cutoff = Instant.now();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.listOlderThan(" ", cutoff, 1)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> storageService.listOlderThan("uploads/", null, 1)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> storageService.listOlderThan("uploads/", cutoff, 0)
                )
        );

        verifyNoInteractions(minioClient);
    }
}
