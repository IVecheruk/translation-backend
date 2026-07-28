package com.translatelab.backend.user.avatar;

public enum AvatarFormat {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png");

    private final String extension;
    private final String contentType;

    AvatarFormat(
            String extension,
            String contentType
    ) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}