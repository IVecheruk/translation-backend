package com.translatelab.backend.subscription.exception;

public class UserSubscriptionNotFoundException extends RuntimeException {
    public UserSubscriptionNotFoundException() {
        super("Подписка платёжного провайдера не найдена");
    }
}
