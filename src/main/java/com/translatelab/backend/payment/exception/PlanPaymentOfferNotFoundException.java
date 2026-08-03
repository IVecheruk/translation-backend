package com.translatelab.backend.payment.exception;

public class PlanPaymentOfferNotFoundException extends RuntimeException {
    public PlanPaymentOfferNotFoundException() {
        super("Платёжное предложение не найдено");
    }
}
