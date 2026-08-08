package com.translatelab.backend.payment.provider.tribute.controller;

import com.translatelab.backend.payment.provider.tribute.service.TributeWebhookService;
import com.translatelab.backend.config.TributeProperties;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import com.translatelab.backend.payment.provider.tribute.exception.TributeWebhookPayloadTooLargeException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;

@Hidden
@RestController
@RequestMapping("/api/payments/webhooks/tribute")
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeWebhookController {

    private static final String SIGNATURE_HEADER =
            "trbt-signature";

    private final TributeWebhookService webhookService;
    private final TributeProperties properties;

    public TributeWebhookController(
            TributeWebhookService webhookService,
            TributeProperties properties
    ) {
        this.webhookService = Objects.requireNonNull(
                webhookService,
                "Сервис webhook Tribute не должен быть null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "Настройки Tribute не должны быть null"
        );
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> processWebhook(
            HttpServletRequest request,

            @RequestHeader(
                    name = SIGNATURE_HEADER,
                    required = false
            )
            String signature
    ) {
        webhookService.processWebhook(
                readBoundedBody(request),
                signature
        );

        return ResponseEntity.ok().build();
    }

    private byte[] readBoundedBody(HttpServletRequest request) {
        int limit = properties.webhookMaxBodyBytes();
        if (request.getContentLengthLong() > limit) {
            throw new TributeWebhookPayloadTooLargeException();
        }
        try {
            byte[] body = request.getInputStream().readNBytes(limit + 1);
            if (body.length > limit) {
                throw new TributeWebhookPayloadTooLargeException();
            }
            return body;
        } catch (IOException exception) {
            throw new InvalidTributeWebhookException(exception);
        }
    }
}
