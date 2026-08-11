package com.translatelab.backend.subscription.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.subscription.dto.AccountSubscriptionResponse;
import com.translatelab.backend.subscription.service.AccountSubscriptionCancellationService;
import com.translatelab.backend.subscription.service.AccountSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountSubscriptionController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class AccountSubscriptionControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOKEN = "valid-token";
    private static final Instant PERIOD_END =
            Instant.parse("2026-09-01T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AccountSubscriptionService service;
    @MockitoBean private AccountSubscriptionCancellationService cancellationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnOnlyUserFacingSubscriptionState() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt());
        given(service.getCurrent(USER_ID)).willReturn(
                new AccountSubscriptionResponse(
                        "PRO",
                        "Профессиональный",
                        "ACTIVE",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        PERIOD_END,
                        true
                )
        );

        mockMvc.perform(get("/api/account/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan_code").value("PRO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.current_period_end")
                        .value(PERIOD_END.toString()))
                .andExpect(jsonPath("$.cancel_at_period_end").value(true))
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.external_order_id").doesNotExist());
    }

    @Test
    void shouldRejectAnonymousSubscriptionRead() throws Exception {
        mockMvc.perform(get("/api/account/subscription"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service, cancellationService, jwtDecoder);
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.parse("2026-08-08T10:00:00Z");
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
