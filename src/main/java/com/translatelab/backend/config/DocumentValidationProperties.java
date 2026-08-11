package com.translatelab.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.document-validation")
@Validated
public record DocumentValidationProperties(
        @NotNull @Valid Docx docx
) {

    public record Docx(
            @Positive int maxEntries,
            @NotNull DataSize maxEntrySize,
            @NotNull DataSize maxTotalUncompressedSize,
            @DecimalMin(value = "0.0", inclusive = false)
            @DecimalMax("1.0")
            double minCompressionRatio
    ) {

        public Docx {
            if (maxEntrySize != null && maxEntrySize.toBytes() <= 0) {
                throw new IllegalArgumentException(
                        "Максимальный размер элемента DOCX должен быть положительным"
                );
            }

            if (maxTotalUncompressedSize != null
                    && maxTotalUncompressedSize.toBytes() <= 0) {
                throw new IllegalArgumentException(
                        "Максимальный распакованный размер DOCX должен быть положительным"
                );
            }

            if (maxEntrySize != null
                    && maxTotalUncompressedSize != null
                    && maxTotalUncompressedSize.toBytes()
                    < maxEntrySize.toBytes()) {
                throw new IllegalArgumentException(
                        "Общий лимит DOCX не может быть меньше лимита одного элемента"
                );
            }
        }
    }
}
