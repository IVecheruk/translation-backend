package com.translatelab.backend.user.avatar;

import com.translatelab.backend.user.exception.InvalidAvatarException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class AvatarValidator {

    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024;
    private static final int HEADER_SIZE = 8;
    private static final byte[] JPEG_SIGNATURE = {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF
    };

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
    };

    public AvatarFormat validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarException(
                    "Файл аватара не должен быть пустым"
            );
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new InvalidAvatarException(
                    "Размер аватара не должен превышать 2 MiB"
            );
        }

        AvatarFormat format = resolveFormat(file.getContentType());

        byte[] header = readHeader(file);

        if (!matchesSignature(format, header)) {
            throw new InvalidAvatarException(
                    "Содержимое файла не соответствует заявленному формату изображения"
            );
        }

        return format;
    }

    private AvatarFormat resolveFormat(String contentType) {
        if (AvatarFormat.JPEG
                .contentType()
                .equals(contentType)) {
            return AvatarFormat.JPEG;
        }

        if (AvatarFormat.PNG
                .contentType()
                .equals(contentType)) {
            return AvatarFormat.PNG;
        }

        throw new InvalidAvatarException(
                "Поддерживаются только изображения JPEG и PNG"
        );
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(HEADER_SIZE);
        } catch (IOException exception) {
            InvalidAvatarException avatarException = new InvalidAvatarException("Не удалось прочитать файл аватара");

            avatarException.addSuppressed(exception);
            throw avatarException;
        }
    }

    private boolean matchesSignature(AvatarFormat format, byte[] header) {
        byte[] expectedSignature = switch (format) {
            case JPEG -> JPEG_SIGNATURE;
            case PNG -> PNG_SIGNATURE;
        };

        return startsWith(header, expectedSignature);
    }

    private boolean startsWith(
            byte[] bytes,
            byte[] prefix
    ) {
        if (bytes.length < prefix.length) {
            return false;
        }

        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }

        return true;
    }
}
