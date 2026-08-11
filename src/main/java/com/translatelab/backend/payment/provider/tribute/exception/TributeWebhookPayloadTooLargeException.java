package com.translatelab.backend.payment.provider.tribute.exception;

public class TributeWebhookPayloadTooLargeException extends RuntimeException {
    public TributeWebhookPayloadTooLargeException() {
        super("Тело webhook Tribute превышает допустимый размер");
    }
}
