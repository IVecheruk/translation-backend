package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.entity.AccountActionTokenType;

public record AccountTokenIssuedEvent(
        String email,
        AccountActionTokenType type,
        String rawToken
) {}
