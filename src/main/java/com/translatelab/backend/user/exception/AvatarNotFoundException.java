package com.translatelab.backend.user.exception;

public class AvatarNotFoundException extends RuntimeException {
    public AvatarNotFoundException() {
        super("Аватар не найден");
    }
}
