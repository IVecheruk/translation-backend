package com.translatelab.backend.payment.controller;

import com.translatelab.backend.config.OpenApiConfig;
import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseStartRequest;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseStartResponse;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCheckoutService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/subscription-purchases")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SubscriptionPurchaseController {

    private final SubscriptionPurchaseCheckoutService checkoutService;
    private final PaymentProperties paymentProperties;

    public SubscriptionPurchaseController(
            SubscriptionPurchaseCheckoutService checkoutService,
            PaymentProperties paymentProperties
    ) {
        this.checkoutService = checkoutService;
        this.paymentProperties = paymentProperties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPurchaseStartResponse startPurchase(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubscriptionPurchaseStartRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        SubscriptionPurchaseIntentCreationCommand command =
                new SubscriptionPurchaseIntentCreationCommand(
                        userId,
                        request.planCode(),
                        paymentProperties.provider()
                );

        return checkoutService.start(command);
    }
}
