package com.translatelab.backend.user.avatar;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

public record ValidatedAvatar(
        AvatarFormat format,
        byte[] content
) {
    public ValidatedAvatar {
        Objects.requireNonNull(format, "Формат аватара не должен быть null");
        Objects.requireNonNull(content, "Содержимое аватара не должно быть null");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }

    public InputStream inputStream() {
        return new ByteArrayInputStream(content);
    }
}
