package com.translatelab.backend.user.avatar;

import com.translatelab.backend.config.AvatarProperties;
import com.translatelab.backend.user.exception.InvalidAvatarException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

@Component
public class AvatarValidator {

    private static final int BYTES_PER_DECODED_PIXEL = 4;

    private final AvatarProperties properties;

    public AvatarValidator(AvatarProperties properties) {
        this.properties = properties;
    }

    public ValidatedAvatar validateAndNormalize(MultipartFile file) {
        validateFileEnvelope(file);
        AvatarFormat declaredFormat = resolveDeclaredFormat(
                file.getContentType()
        );
        byte[] source = readSource(file);

        try (var imageInput = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(source)
        )) {
            ImageReader reader = requireReader(imageInput);
            try {
                reader.setInput(imageInput, true, true);
                AvatarFormat decodedFormat = resolveDecodedFormat(
                        reader.getFormatName()
                );
                if (decodedFormat != declaredFormat) {
                    throw invalid(
                            "Содержимое файла не соответствует заявленному формату изображения"
                    );
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalid("Не удалось декодировать изображение аватара");
                }
                BufferedImage canonical = canonicalize(
                        decoded,
                        decodedFormat
                );
                byte[] normalized = encode(canonical, decodedFormat);
                if (normalized.length == 0
                        || normalized.length
                        > properties.maxNormalizedSize().toBytes()) {
                    throw invalid(
                            "Нормализованный аватар превышает допустимый размер"
                    );
                }
                return new ValidatedAvatar(decodedFormat, normalized);
            } finally {
                reader.dispose();
            }
        } catch (InvalidAvatarException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid(
                    "Файл не является корректным изображением JPEG или PNG",
                    exception
            );
        }
    }

    private void validateFileEnvelope(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Файл аватара не должен быть пустым");
        }
        if (file.getSize() > properties.maxUploadSize().toBytes()) {
            throw invalid("Размер аватара превышает допустимый");
        }
    }

    private byte[] readSource(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw invalid("Не удалось прочитать файл аватара", exception);
        }
    }

    private ImageReader requireReader(
            MemoryCacheImageInputStream imageInput
    ) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
        if (!readers.hasNext()) {
            throw invalid("Файл не является корректным изображением JPEG или PNG");
        }
        return readers.next();
    }

    private AvatarFormat resolveDeclaredFormat(String contentType) {
        for (AvatarFormat format : AvatarFormat.values()) {
            if (format.contentType().equals(contentType)) {
                return format;
            }
        }
        throw invalid("Поддерживаются только изображения JPEG и PNG");
    }

    private AvatarFormat resolveDecodedFormat(String formatName) {
        return switch (formatName.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> AvatarFormat.JPEG;
            case "png" -> AvatarFormat.PNG;
            default -> throw invalid(
                    "Поддерживаются только изображения JPEG и PNG"
            );
        };
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0
                || height <= 0
                || width > properties.maxWidth()
                || height > properties.maxHeight()) {
            throw invalid("Размеры изображения аватара превышают допустимые");
        }

        long pixels;
        long decodedBytes;
        try {
            pixels = Math.multiplyExact((long) width, height);
            decodedBytes = Math.multiplyExact(
                    pixels,
                    BYTES_PER_DECODED_PIXEL
            );
        } catch (ArithmeticException exception) {
            throw invalid(
                    "Размеры изображения аватара превышают допустимые",
                    exception
            );
        }
        if (pixels > properties.maxPixels()
                || decodedBytes
                > properties.maxDecodedMemory().toBytes()) {
            throw invalid("Декодированный аватар превышает допустимый объём памяти");
        }
    }

    private BufferedImage canonicalize(
            BufferedImage decoded,
            AvatarFormat format
    ) {
        int type = format == AvatarFormat.PNG && decoded.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage canonical = new BufferedImage(
                decoded.getWidth(),
                decoded.getHeight(),
                type
        );
        Graphics2D graphics = canonical.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            if (type == BufferedImage.TYPE_INT_RGB) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(
                        0,
                        0,
                        canonical.getWidth(),
                        canonical.getHeight()
                );
            }
            graphics.drawImage(decoded, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return canonical;
    }

    private byte[] encode(
            BufferedImage image,
            AvatarFormat format
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(
                format == AvatarFormat.JPEG ? "jpeg" : "png"
        );
        if (!writers.hasNext()) {
            throw invalid("На сервере отсутствует кодировщик формата аватара");
        }
        ImageWriter writer = writers.next();
        try (var imageOutput = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (format == AvatarFormat.JPEG
                    && writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(
                        ImageWriteParam.MODE_EXPLICIT
                );
                writeParam.setCompressionQuality(0.9F);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private InvalidAvatarException invalid(String message) {
        return new InvalidAvatarException(message);
    }

    private InvalidAvatarException invalid(
            String message,
            Exception cause
    ) {
        InvalidAvatarException exception = new InvalidAvatarException(message);
        exception.addSuppressed(cause);
        return exception;
    }
}
