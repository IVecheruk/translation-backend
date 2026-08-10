package com.translatelab.backend.auth.controller;

import com.translatelab.backend.auth.dto.LoginRequest;
import com.translatelab.backend.auth.dto.LoginResponse;
import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.dto.RegisterResponse;
import com.translatelab.backend.auth.service.LoginService;
import com.translatelab.backend.auth.service.RegistrationService;
import com.translatelab.backend.auth.service.AccountRecoveryService;
import com.translatelab.backend.auth.service.SessionRevocationService;
import com.translatelab.backend.auth.dto.AccountEmailRequest;
import com.translatelab.backend.auth.dto.AccountTokenRequest;
import com.translatelab.backend.auth.dto.PasswordResetConfirmRequest;
import com.translatelab.backend.auth.security.LoginAttemptLimiter;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.translatelab.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final AccountRecoveryService recoveryService;
    private final SessionRevocationService sessionRevocationService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            AccountRecoveryService recoveryService,
            SessionRevocationService sessionRevocationService,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.recoveryService = recoveryService;
        this.sessionRevocationService = sessionRevocationService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login (
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        loginAttemptLimiter.check(
                request.email(),
                httpRequest.getRemoteAddr()
        );
        return loginService.login(request);
    }

    @PostMapping("/email-verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailVerification(
            @Valid @RequestBody AccountEmailRequest request
    ) {
        recoveryService.requestEmailVerification(request.email());
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmail(
            @Valid @RequestBody AccountTokenRequest request
    ) {
        recoveryService.confirmEmail(request.token());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(
            @Valid @RequestBody AccountEmailRequest request
    ) {
        recoveryService.requestPasswordReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        recoveryService.resetPassword(request.token(), request.password());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        sessionRevocationService.revokeAll(
                UUID.fromString(jwt.getSubject())
        );
    }
}
