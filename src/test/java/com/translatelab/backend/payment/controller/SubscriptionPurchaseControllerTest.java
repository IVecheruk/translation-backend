package com.translatelab.backend.payment.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseStartResponse;
import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import com.translatelab.backend.payment.exception.PlanPaymentOfferNotFoundException;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCheckoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionPurchaseController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class SubscriptionPurchaseControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "07036527-668c-4072-87ee-ab04b7535e68"
    );
    private static final UUID INTENT_ID = UUID.fromString(
            "3d3284fa-86ae-4af0-973b-e210a63bad47"
    );
    private static final String TOKEN = "valid-token";
    private static final Instant EXPIRES_AT = Instant.parse(
            "2026-09-01T00:30:00Z"
    );
    private static final URI REDIRECT_URL = URI.create(
            "https://pay.example.com/checkout/123"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionPurchaseCheckoutService checkoutService;

    @MockitoBean
    private PaymentProperties paymentProperties;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreatePurchaseFromAuthenticatedAndServerValues()
            throws Exception {
        SubscriptionPurchaseIntentCreationCommand command =
                expectedCommand();
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(paymentProperties.provider()).willReturn("TRIBUTE");
        given(checkoutService.start(command)).willReturn(
                new SubscriptionPurchaseStartResponse(
                        INTENT_ID,
                        "TRIBUTE",
                        REDIRECT_URL,
                        EXPIRES_AT
                )
        );

        mockMvc.perform(post("/api/subscription-purchases")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_code\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.intent_id")
                        .value(INTENT_ID.toString()))
                .andExpect(jsonPath("$.provider")
                        .value("TRIBUTE"))
                .andExpect(jsonPath("$.redirect_url")
                        .value(REDIRECT_URL.toString()))
                .andExpect(jsonPath("$.expires_at")
                        .value(EXPIRES_AT.toString()));

        verify(jwtDecoder).decode(TOKEN);
        verify(paymentProperties).provider();
        verify(checkoutService).start(command);
    }

    @Test
    void shouldReturnNotFoundForUnavailableOffer() throws Exception {
        SubscriptionPurchaseIntentCreationCommand command =
                expectedCommand();
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(paymentProperties.provider()).willReturn("TRIBUTE");
        given(checkoutService.start(command)).willThrow(
                new PlanPaymentOfferNotFoundException()
        );

        mockMvc.perform(post("/api/subscription-purchases")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_code\":\"PRO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Платёжное предложение не найдено"))
                .andExpect(jsonPath("$.path")
                        .value("/api/subscription-purchases"));

        verify(checkoutService).start(command);
    }

    @Test
    void shouldReturnServiceUnavailableWhenProviderIsUnavailable()
            throws Exception {
        SubscriptionPurchaseIntentCreationCommand command =
                expectedCommand();
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(paymentProperties.provider()).willReturn("TRIBUTE");
        given(checkoutService.start(command)).willThrow(
                new PaymentProviderUnavailableException()
        );

        mockMvc.perform(post("/api/subscription-purchases")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_code\":\"PRO\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Платёжный сервис временно недоступен"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/subscription-purchases"
                ));

        verify(checkoutService).start(command);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"plan_code\":null}",
            "{\"plan_code\":\" \"}",
            "{\"plan_code\":\"pro\"}",
            "{\"plan_code\":\"PRO-PLUS\"}"
    })
    void shouldRejectInvalidRequestBeforeControllerLogic(String json)
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(post("/api/subscription-purchases")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path")
                        .value("/api/subscription-purchases"));

        verifyNoInteractions(paymentProperties, checkoutService);
    }

    @Test
    void shouldRejectPurchaseWithoutToken() throws Exception {
        mockMvc.perform(post("/api/subscription-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_code\":\"PRO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/subscription-purchases"));

        verifyNoInteractions(
                jwtDecoder,
                paymentProperties,
                checkoutService
        );
    }

    @Test
    void shouldRejectPurchaseUntilEmailIsVerified() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt(false));

        mockMvc.perform(post("/api/subscription-purchases")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_code\":\"PRO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Доступ запрещён"));

        verifyNoInteractions(paymentProperties, checkoutService);
    }

    private SubscriptionPurchaseIntentCreationCommand expectedCommand() {
        return new SubscriptionPurchaseIntentCreationCommand(
                USER_ID,
                "PRO",
                "TRIBUTE"
        );
    }

    private Jwt createJwt() {
        return createJwt(true);
    }

    private Jwt createJwt(boolean emailVerified) {
        Instant issuedAt = Instant.parse("2026-08-01T06:00:00Z");

        return Jwt.withTokenValue(TOKEN)
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .claim("email_verified", emailVerified)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
