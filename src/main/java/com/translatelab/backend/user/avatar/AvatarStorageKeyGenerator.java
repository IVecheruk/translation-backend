package com.translatelab.backend.user.avatar;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class AvatarStorageKeyGenerator {

    private static final String AVATARS_PREFIX = "avatars";

    public String generateAvatarKey(
            UUID userId,
            AvatarFormat format
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        Objects.requireNonNull(
                format,
                "Формат аватара не должен быть null"
        );

        UUID objectId = UUID.randomUUID();

        return AVATARS_PREFIX
                + "/"
                + userId
                + "/"
                + objectId
                + "."
                + format.extension();
    }
}