package com.translatelab.backend.storage.exception;

public class StorageException extends RuntimeException {
    public StorageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
