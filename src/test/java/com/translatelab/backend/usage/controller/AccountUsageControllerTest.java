package com.translatelab.backend.usage.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.usage.dto.AccountUsageResponse;
import com.translatelab.backend.usage.service.AccountUsageService;
import com.translatelab.backend.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountUsageController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class AccountUsageControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "07036527-668c-4072-87ee-ab04b7535e68"
    );
    private static final String TOKEN = "valid-token";
    private static final Instant RESETS_AT =
            Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountUsageService accountUsageService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnAuthenticatedUsersCurrentUsage() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(accountUsageService.getCurrentUsage(USER_ID))
                .willReturn(createResponse());

        mockMvc.perform(get("/api/account/usage")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.plan_code")
                        .value("FREE"))
                .andExpect(jsonPath("$.plan_display_name")
                        .value("Бесплатный"))
                .andExpect(jsonPath("$.feature_code")
                        .value("DOCUMENT_TRANSLATION"))
                .andExpect(jsonPath("$.period_type")
                        .value("MONTH"))
                .andExpect(jsonPath("$.unlimited")
                        .value(false))
                .andExpect(jsonPath("$.limit_units")
                        .value(5))
                .andExpect(jsonPath("$.used_units")
                        .value(2))
                .andExpect(jsonPath("$.remaining_units")
                        .value(3))
                .andExpect(jsonPath("$.resets_at")
                        .value(RESETS_AT.toString()));

        verify(jwtDecoder).decode(TOKEN);
        verify(accountUsageService).getCurrentUsage(USER_ID);
    }

    @Test
    void shouldReturnNotFoundWhenAccountIsMissing() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(accountUsageService.getCurrentUsage(USER_ID))
                .willThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/account/usage")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Пользователь не найден"))
                .andExpect(jsonPath("$.path")
                        .value("/api/account/usage"));

        verify(accountUsageService).getCurrentUsage(USER_ID);
    }

    @Test
    void shouldRejectUsageReadWithoutToken() throws Exception {
        mockMvc.perform(get("/api/account/usage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/account/usage"));

        verifyNoInteractions(jwtDecoder, accountUsageService);
    }

    private AccountUsageResponse createResponse() {
        return new AccountUsageResponse(
                "FREE",
                "Бесплатный",
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH,
                false,
                5,
                2,
                3L,
                RESETS_AT
        );
    }

    private Jwt createJwt() {
        Instant issuedAt = Instant.parse("2026-08-01T06:00:00Z");

        return Jwt.withTokenValue(TOKEN)
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
