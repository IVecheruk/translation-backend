package com.translatelab.backend.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Сессия истекла или отозвана. Войдите повторно");
    }
}
