package com.translatelab.backend.payment.exception;

public class SubscriptionPurchaseConflictException extends RuntimeException {

    public SubscriptionPurchaseConflictException() {
        super("У пользователя уже есть активная подписка или незавершённая покупка");
    }
}
