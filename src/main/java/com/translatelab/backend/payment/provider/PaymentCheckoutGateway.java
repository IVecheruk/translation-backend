package com.translatelab.backend.payment.provider;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;

public interface PaymentCheckoutGateway {
    String providerCode();

    PaymentCheckoutResult createCheckout(
            PaymentCheckoutCreationCommand command
    );
}