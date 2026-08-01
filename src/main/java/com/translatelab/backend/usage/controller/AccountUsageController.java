package com.translatelab.backend.usage.controller;

import com.translatelab.backend.config.OpenApiConfig;
import com.translatelab.backend.usage.dto.AccountUsageResponse;
import com.translatelab.backend.usage.service.AccountUsageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/account/usage")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountUsageController {

    private final AccountUsageService accountUsageService;

    public AccountUsageController(
            AccountUsageService accountUsageService
    ) {
        this.accountUsageService = accountUsageService;
    }

    @GetMapping
    public AccountUsageResponse getCurrentUsage(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return accountUsageService.getCurrentUsage(userId);
    }
}