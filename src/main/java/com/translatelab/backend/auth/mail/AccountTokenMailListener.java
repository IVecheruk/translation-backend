package com.translatelab.backend.auth.mail;

import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.auth.service.AccountTokenIssuedEvent;
import com.translatelab.backend.config.AccountSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class AccountTokenMailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AccountTokenMailListener.class
    );

    private final AccountMailGateway mailGateway;
    private final AccountSecurityProperties properties;

    public AccountTokenMailListener(
            AccountMailGateway mailGateway,
            AccountSecurityProperties properties
    ) {
        this.mailGateway = mailGateway;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(AccountTokenIssuedEvent event) {
        try {
            URI actionUrl = actionUrl(event);
            if (event.type() == AccountActionTokenType.EMAIL_VERIFICATION) {
                mailGateway.sendEmailVerification(event.email(), actionUrl);
            } else {
                mailGateway.sendPasswordReset(event.email(), actionUrl);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Не удалось доставить письмо для операции аккаунта типа {}",
                    event.type(),
                    exception
            );
        }
    }

    private URI actionUrl(AccountTokenIssuedEvent event) {
        String path = event.type() == AccountActionTokenType.EMAIL_VERIFICATION
                ? "/verify-email"
                : "/reset-password";
        return UriComponentsBuilder
                .fromUri(properties.frontendBaseUrl())
                .path(path)
                .queryParam("token", event.rawToken())
                .build()
                .encode()
                .toUri();
    }
}
