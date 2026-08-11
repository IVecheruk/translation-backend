package com.translatelab.backend.auth.exception;

public class InvalidAccountActionTokenException extends RuntimeException {

    public InvalidAccountActionTokenException() {
        super("Одноразовая ссылка недействительна или срок её действия истёк");
    }
}
