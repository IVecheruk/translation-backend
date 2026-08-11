package com.translatelab.backend.user.avatar;

import com.translatelab.backend.config.AvatarProperties;
import com.translatelab.backend.user.exception.InvalidAvatarException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AvatarValidatorTest {

    private final AvatarValidator validator = new AvatarValidator(properties(
            4096,
            4096,
            16_777_216,
            DataSize.ofMegabytes(64)
    ));

    @Test
    void shouldDecodeAndNormalizeJpeg() throws Exception {
        byte[] source = image("jpeg", 32, 24, false);

        ValidatedAvatar result = validator.validateAndNormalize(file(
                "image/jpeg",
                source
        ));

        assertEquals(AvatarFormat.JPEG, result.format());
        assertNotNull(ImageIO.read(result.inputStream()));
        assertTrue(result.size() > 0);
    }

    @Test
    void shouldDecodeAndNormalizePngWithAlpha() throws Exception {
        byte[] source = image("png", 20, 30, true);

        ValidatedAvatar result = validator.validateAndNormalize(file(
                "image/png",
                source
        ));

        assertEquals(AvatarFormat.PNG, result.format());
        BufferedImage decoded = ImageIO.read(result.inputStream());
        assertNotNull(decoded);
        assertTrue(decoded.getColorModel().hasAlpha());
    }

    @Test
    void shouldRemoveTrailingUntrustedDataDuringNormalization()
            throws Exception {
        byte[] image = image("png", 10, 10, false);
        byte[] marker = "private-metadata".getBytes();
        byte[] source = Arrays.copyOf(image, image.length + marker.length);
        System.arraycopy(marker, 0, source, image.length, marker.length);

        ValidatedAvatar result = validator.validateAndNormalize(file(
                "image/png",
                source
        ));

        assertTrue(result.size() < source.length);
        assertFalse(endsWith(result.content(), marker));
    }

    @Test
    void shouldRejectMalformedImageWithValidSignature() {
        byte[] malformed = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x01, 0x02, 0x03
        };

        assertInvalid(file("image/png", malformed));
    }

    @Test
    void shouldRejectDeclaredAndDecodedFormatMismatch() throws Exception {
        assertInvalid(file(
                "image/jpeg",
                image("png", 10, 10, false)
        ));
    }

    @Test
    void shouldRejectOversizedDimensionsBeforeDecode() throws Exception {
        AvatarValidator strict = new AvatarValidator(properties(
                64,
                64,
                4096,
                DataSize.ofKilobytes(16)
        ));

        assertThrows(
                InvalidAvatarException.class,
                () -> strict.validateAndNormalize(file(
                        "image/png",
                        image("png", 65, 10, false)
                ))
        );
    }

    @Test
    void shouldRejectDecodedMemoryLimit() throws Exception {
        AvatarValidator strict = new AvatarValidator(properties(
                100,
                100,
                10_000,
                DataSize.ofBytes(399)
        ));

        assertThrows(
                InvalidAvatarException.class,
                () -> strict.validateAndNormalize(file(
                        "image/png",
                        image("png", 10, 10, false)
                ))
        );
    }

    @Test
    void shouldRejectNullAndEmptyFiles() {
        assertInvalid(null);
        assertInvalid(file("image/png", new byte[0]));
    }

    @Test
    void shouldRejectFileOverUploadLimit() {
        byte[] source = new byte[(2 * 1024 * 1024) + 1];
        assertInvalid(file("image/png", source));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"image/gif", "image/jpg", "application/octet-stream"})
    void shouldRejectUnsupportedContentType(String contentType)
            throws Exception {
        assertInvalid(file(
                contentType,
                image("png", 10, 10, false)
        ));
    }

    @Test
    void shouldPreserveReadFailure() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        IOException cause = new IOException("read failure");
        given(file.isEmpty()).willReturn(false);
        given(file.getSize()).willReturn(100L);
        given(file.getContentType()).willReturn("image/jpeg");
        given(file.getBytes()).willThrow(cause);

        InvalidAvatarException exception = assertThrows(
                InvalidAvatarException.class,
                () -> validator.validateAndNormalize(file)
        );

        assertEquals("Не удалось прочитать файл аватара", exception.getMessage());
        assertEquals(1, exception.getSuppressed().length);
        assertSame(cause, exception.getSuppressed()[0]);
    }

    private AvatarProperties properties(
            int maxWidth,
            int maxHeight,
            long maxPixels,
            DataSize maxDecodedMemory
    ) {
        return new AvatarProperties(
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(2),
                maxDecodedMemory,
                maxWidth,
                maxHeight,
                maxPixels,
                Duration.ofHours(1),
                Duration.ofHours(1),
                100
        );
    }

    private MockMultipartFile file(String contentType, byte[] content) {
        return new MockMultipartFile(
                "file",
                "avatar",
                contentType,
                content
        );
    }

    private byte[] image(
            String format,
            int width,
            int height,
            boolean alpha
    ) throws IOException {
        BufferedImage image = new BufferedImage(
                width,
                height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(30, 80, 140, alpha ? 120 : 255));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private boolean endsWith(byte[] value, byte[] suffix) {
        if (suffix.length > value.length) {
            return false;
        }
        for (int index = 1; index <= suffix.length; index++) {
            if (value[value.length - index] != suffix[suffix.length - index]) {
                return false;
            }
        }
        return true;
    }

    private void assertInvalid(MultipartFile file) {
        assertThrows(
                InvalidAvatarException.class,
                () -> validator.validateAndNormalize(file)
        );
    }
}
