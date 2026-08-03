package com.translatelab.backend.plan.exception;

public class SubscriptionPlanNotFoundException extends RuntimeException {
    public SubscriptionPlanNotFoundException() {
        super("Тариф подписки не найден");
    }
}
