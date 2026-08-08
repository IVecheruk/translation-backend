package com.translatelab.backend.translation.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum FileFormat {
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    DOC("application/msword"),
    PDF("application/pdf");

    private final String contentType;

    FileFormat(String contentType) {
        this.contentType = contentType;
    }

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String contentType() {
        return contentType;
    }
}
