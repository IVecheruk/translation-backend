package com.translatelab.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.document-retention")
@Validated
public record DocumentRetentionProperties(
        @NotNull Duration completedSourceRetention,
        @NotNull Duration failedSourceRetention,
        @NotNull Duration resultRetention,
        @NotNull Duration abandonedUploadRetention,
        @NotNull Duration abandonedJobTimeout,
        @NotNull Duration cleanupInterval,
        @Min(1) @Max(1000) int cleanupBatchSize
) {

    public DocumentRetentionProperties {
        requirePositive(completedSourceRetention, "Срок хранения исходного документа");
        requirePositive(failedSourceRetention, "Срок хранения документа ошибочного задания");
        requirePositive(resultRetention, "Срок хранения результата");
        requirePositive(abandonedUploadRetention, "Срок хранения брошенной загрузки");
        requirePositive(abandonedJobTimeout, "Тайм-аут брошенного задания");
        requirePositive(cleanupInterval, "Интервал очистки документов");

        if (cleanupBatchSize <= 0 || cleanupBatchSize > 1000) {
            throw new IllegalArgumentException(
                    "Размер пакета очистки документов должен быть от 1 до 1000"
            );
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " должен быть положительным");
        }
    }
}
