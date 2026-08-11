package com.translatelab.backend.payment.provider.tribute.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookSignatureException;
import com.translatelab.backend.payment.provider.tribute.service.TributeWebhookService;
import com.translatelab.backend.config.TributeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = TributeWebhookController.class,
        properties = "app.payment.tribute.enabled=true"
)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class TributeWebhookSecurityTest {

    private static final String PATH =
            "/api/payments/webhooks/tribute";
    private static final String SIGNATURE = "a".repeat(64);
    private static final byte[] RAW_BODY = "{\"name\":\"shop_order\"}"
            .getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TributeWebhookService webhookService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TributeProperties tributeProperties;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void configureLimits() {
        org.mockito.Mockito.lenient()
                .when(tributeProperties.webhookMaxBodyBytes())
                .thenReturn(65_536);
        org.mockito.Mockito.lenient()
                .when(tributeProperties.webhookRequestsPerMinute())
                .thenReturn(120);
        org.mockito.Mockito.lenient()
                .when(clock.instant())
                .thenReturn(Instant.parse("2026-08-08T12:00:00Z"));
    }

    @Test
    void shouldPermitExactPostWebhookWithoutJwt() throws Exception {
        given(webhookService.processWebhook(
                any(byte[].class),
                eq(SIGNATURE)
        )).willReturn(true);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("trbt-signature", SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(webhookService).processWebhook(
                any(byte[].class),
                eq(SIGNATURE)
        );
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void shouldReachSignatureAuthenticationWithoutJwt()
            throws Exception {
        given(webhookService.processWebhook(
                any(byte[].class),
                isNull()
        )).willThrow(
                new InvalidTributeWebhookSignatureException()
        );

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RAW_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Недействительная подпись webhook Tribute"
                ))
                .andExpect(jsonPath("$.path").value(PATH));

        verify(webhookService).processWebhook(
                any(byte[].class),
                isNull()
        );
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void shouldKeepGetOnWebhookPathProtected() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Требуется аутентификация"
                ));

        verifyNoInteractions(jwtDecoder, webhookService);
    }

    @Test
    void shouldKeepNestedWebhookPathProtected() throws Exception {
        mockMvc.perform(post(PATH + "/unexpected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RAW_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Требуется аутентификация"
                ));

        verifyNoInteractions(jwtDecoder, webhookService);
    }

    @Test
    void shouldKeepUserPurchaseEndpointProtected() throws Exception {
        mockMvc.perform(post("/api/subscription-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_code\":\"PRO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Требуется аутентификация"
                ));

        verifyNoInteractions(jwtDecoder, webhookService);
    }
}
