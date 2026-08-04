package com.translatelab.backend.payment.exception;

public class PaymentProviderUnavailableException extends RuntimeException {

    private static final String MESSAGE =
            "Платёжный сервис временно недоступен";


    public PaymentProviderUnavailableException() {
        super(MESSAGE);
    }

    public PaymentProviderUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
