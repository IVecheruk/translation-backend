package com.translatelab.backend.payment.provider;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;

public interface PaymentCheckoutGateway {
    String providerCode();

    PaymentCheckoutResult createCheckout(
            PaymentCheckoutCreationCommand command
    );

    default void cancelCheckout(String externalCheckoutId) {
        throw new UnsupportedOperationException(
                "Провайдер не поддерживает компенсационную отмену checkout"
        );
    }
}
