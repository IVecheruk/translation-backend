package com.translatelab.backend.subscription.exception;

public class SubscriptionCancellationNotSupportedException
        extends RuntimeException {
    public SubscriptionCancellationNotSupportedException() {
        super("Эта подписка не поддерживает отмену через платёжного провайдера");
    }
}
