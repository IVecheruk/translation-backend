package com.translatelab.backend.payment.provider.tribute.exception;

public class InvalidTributeWebhookSignatureException extends RuntimeException {
    public InvalidTributeWebhookSignatureException() {
        super("Недействительная подпись webhook Tribute");
    }
}
