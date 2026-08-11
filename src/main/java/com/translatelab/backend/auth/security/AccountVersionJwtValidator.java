package com.translatelab.backend.auth.security;

import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class AccountVersionJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "Access token отозван или принадлежит недоступному аккаунту",
            null
    );

    private final UserRepository userRepository;
    private final String requiredKeyId;

    public AccountVersionJwtValidator(
            UserRepository userRepository,
            String requiredKeyId
    ) {
        this.userRepository = userRepository;
        this.requiredKeyId = requiredKeyId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if (!requiredKeyId.equals(jwt.getHeaders().get("kid"))) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            UUID userId = UUID.fromString(jwt.getSubject());
            Number claimedVersion = jwt.getClaim("auth_version");
            Boolean claimedEmailVerified = jwt.getClaimAsBoolean(
                    "email_verified"
            );
            if (claimedVersion == null || claimedEmailVerified == null) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null
                    || user.getAuthVersion() != claimedVersion.longValue()
                    || user.isEmailVerified() != claimedEmailVerified) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
    }
}
