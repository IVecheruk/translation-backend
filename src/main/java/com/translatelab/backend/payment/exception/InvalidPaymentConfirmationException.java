package com.translatelab.backend.payment.exception;

public class InvalidPaymentConfirmationException extends RuntimeException {

    public InvalidPaymentConfirmationException() {
        super("Параметры подтверждённого платежа не совпадают с заявкой");
    }
}
