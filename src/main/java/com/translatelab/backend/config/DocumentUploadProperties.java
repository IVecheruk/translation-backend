package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.document-upload")
@Validated
public record DocumentUploadProperties(

        @NotNull
        DataSize maxFileSize
) {

    public DocumentUploadProperties {
        if (maxFileSize != null && (maxFileSize.toBytes()) <= 0) {
            throw new IllegalArgumentException("Максимальный размер документа должен быть положительным");
        }
    }
}