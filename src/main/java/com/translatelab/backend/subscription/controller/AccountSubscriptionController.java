package com.translatelab.backend.subscription.controller;

import com.translatelab.backend.config.OpenApiConfig;
import com.translatelab.backend.subscription.dto.AccountSubscriptionResponse;
import com.translatelab.backend.subscription.service.AccountSubscriptionService;
import com.translatelab.backend.subscription.service.AccountSubscriptionCancellationService;
import com.translatelab.backend.subscription.dto.SubscriptionCancellationResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@RestController
@RequestMapping("/api/account/subscription")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountSubscriptionController {

    private final AccountSubscriptionService service;
    private final AccountSubscriptionCancellationService cancellationService;

    public AccountSubscriptionController(
            AccountSubscriptionService service,
            AccountSubscriptionCancellationService cancellationService
    ) {
        this.service = service;
        this.cancellationService = cancellationService;
    }

    @GetMapping
    public AccountSubscriptionResponse getCurrent(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.getCurrent(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/cancellation")
    public SubscriptionCancellationResponse cancel(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return cancellationService.cancel(UUID.fromString(jwt.getSubject()));
    }
}
