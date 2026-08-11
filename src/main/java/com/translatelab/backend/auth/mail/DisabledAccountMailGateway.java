package com.translatelab.backend.auth.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConditionalOnProperty(
        prefix = "app.account-security",
        name = "email-delivery-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledAccountMailGateway implements AccountMailGateway {

    @Override
    public void sendEmailVerification(String email, URI actionUrl) {
        // Доставка намеренно выключена в локальной среде.
    }

    @Override
    public void sendPasswordReset(String email, URI actionUrl) {
        // Доставка намеренно выключена в локальной среде.
    }
}
