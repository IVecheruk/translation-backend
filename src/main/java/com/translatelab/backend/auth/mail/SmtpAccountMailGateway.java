package com.translatelab.backend.auth.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URI;
import com.translatelab.backend.config.AccountSecurityProperties;

@Component
@ConditionalOnProperty(
        prefix = "app.account-security",
        name = "email-delivery-enabled",
        havingValue = "true"
)
public class SmtpAccountMailGateway implements AccountMailGateway {

    private final JavaMailSender mailSender;
    private final AccountSecurityProperties properties;

    public SmtpAccountMailGateway(
            JavaMailSender mailSender,
            AccountSecurityProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendEmailVerification(String email, URI actionUrl) {
        send(
                email,
                "Подтверждение email TranslateLab",
                "Подтвердите email по одноразовой ссылке: " + actionUrl
        );
    }

    @Override
    public void sendPasswordReset(String email, URI actionUrl) {
        send(
                email,
                "Сброс пароля TranslateLab",
                "Установите новый пароль по одноразовой ссылке: " + actionUrl
        );
    }

    private void send(String email, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.senderEmail());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
