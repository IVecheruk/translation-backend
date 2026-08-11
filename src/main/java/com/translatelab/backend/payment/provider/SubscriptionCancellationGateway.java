package com.translatelab.backend.payment.provider;

public interface SubscriptionCancellationGateway {

    String providerCode();

    boolean requestCancellation(String externalOrderId);
}
