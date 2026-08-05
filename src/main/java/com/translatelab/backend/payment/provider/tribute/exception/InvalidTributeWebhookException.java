package com.translatelab.backend.payment.provider.tribute.exception;

public class InvalidTributeWebhookException
        extends RuntimeException {

    private static final String MESSAGE =
            "Некорректные данные webhook Tribute";

    public InvalidTributeWebhookException() {
        super(MESSAGE);
    }

    public InvalidTributeWebhookException(
            Throwable cause
    ) {
        super(MESSAGE, cause);
    }
}
