package com.translatelab.backend.usage.exception;

public class UsageReservationNotFoundException extends RuntimeException {
    public UsageReservationNotFoundException() {
        super("Резервация использования не найдена");
    }
}
