package com.translatelab.backend.translation.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum FileFormat {
    DOCX,
    DOC,
    PDF;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}