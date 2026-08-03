package com.translatelab.backend.payment.exception;

public class SubscriptionPurchaseIntentNotFoundException extends RuntimeException {
    public SubscriptionPurchaseIntentNotFoundException() {
        super("Заявка на покупку подписки не найдена");
    }
}
