package com.translatelab.backend.user.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvatarDownloadResultTest {

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png"})
    void shouldPreserveDownloadData(String contentType) {
        InputStream inputStream = new ByteArrayInputStream(
                new byte[]{1, 2, 3}
        );

        AvatarDownloadResult result = new AvatarDownloadResult(
                inputStream,
                contentType
        );

        assertSame(inputStream, result.inputStream());
        assertEquals(contentType, result.contentType());
    }

    @Test
    void shouldRejectNullInputStream() {
        assertThrows(
                NullPointerException.class,
                () -> new AvatarDownloadResult(
                        null,
                        "image/jpeg"
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingContentType(String contentType) {
        InputStream inputStream = new ByteArrayInputStream(
                new byte[]{1, 2, 3}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AvatarDownloadResult(
                        inputStream,
                        contentType
                )
        );
    }
}
