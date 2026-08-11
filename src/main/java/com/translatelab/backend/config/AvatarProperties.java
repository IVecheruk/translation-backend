package com.translatelab.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.avatar")
@Validated
public record AvatarProperties(
        @NotNull DataSize maxUploadSize,
        @NotNull DataSize maxNormalizedSize,
        @NotNull DataSize maxDecodedMemory,
        @Min(1) int maxWidth,
        @Min(1) int maxHeight,
        @Min(1) long maxPixels,
        @NotNull Duration orphanRetention,
        @NotNull Duration cleanupInterval,
        @Min(1) int cleanupBatchSize
) {
    public AvatarProperties {
        requirePositive(maxUploadSize, "max-upload-size");
        requirePositive(maxNormalizedSize, "max-normalized-size");
        requirePositive(maxDecodedMemory, "max-decoded-memory");
        requirePositive(orphanRetention, "orphan-retention");
        requirePositive(cleanupInterval, "cleanup-interval");
    }

    private static void requirePositive(DataSize value, String name) {
        if (value != null && value.toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "app.avatar." + name + " должен быть положительным"
            );
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(
                    "app.avatar." + name + " должен быть положительным"
            );
        }
    }
}
