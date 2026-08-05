package com.translatelab.backend.payment.provider.tribute.controller;

import com.translatelab.backend.payment.provider.tribute.service.TributeWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

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

    public TributeWebhookController(
            TributeWebhookService webhookService
    ) {
        this.webhookService = Objects.requireNonNull(
                webhookService,
                "Сервис webhook Tribute не должен быть null"
        );
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> processWebhook(
            @RequestBody(required = false)
            byte[] rawBody,

            @RequestHeader(
                    name = SIGNATURE_HEADER,
                    required = false
            )
            String signature
    ) {
        webhookService.processWebhook(
                rawBody,
                signature
        );

        return ResponseEntity.ok().build();
    }
}
