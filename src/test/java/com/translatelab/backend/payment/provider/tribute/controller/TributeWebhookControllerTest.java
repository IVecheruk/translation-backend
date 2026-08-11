package com.translatelab.backend.payment.provider.tribute.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookSignatureException;
import com.translatelab.backend.payment.provider.tribute.service.TributeWebhookService;
import com.translatelab.backend.config.TributeProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class TributeWebhookControllerTest {

    private static final String PATH =
            "/api/payments/webhooks/tribute";
    private static final String SIGNATURE = "a".repeat(64);
    private static final byte[] RAW_BODY = """
            {
              "name": "shop_order",
              "payload": {
                "title": "Подписка"
              }
            }
            """.getBytes(StandardCharsets.UTF_8);

    @Mock
    private TributeWebhookService webhookService;
    @Mock
    private TributeProperties properties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(properties.webhookMaxBodyBytes())
                .thenReturn(65_536);
        mockMvc = standaloneSetup(
                new TributeWebhookController(webhookService, properties)
        )
                .setControllerAdvice(new GlobalExceptionHandler(
                        java.util.Optional.of(new SimpleMeterRegistry())
                ))
                .build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnOkForNewAndDuplicateValidWebhook(
            boolean newlyProcessed
    ) throws Exception {
        given(webhookService.processWebhook(
                any(byte[].class),
                eq(SIGNATURE)
        )).willReturn(newlyProcessed);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("trbt-signature", SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        ArgumentCaptor<byte[]> bodyCaptor =
                ArgumentCaptor.forClass(byte[].class);
        verify(webhookService).processWebhook(
                bodyCaptor.capture(),
                eq(SIGNATURE)
        );
        assertArrayEquals(RAW_BODY, bodyCaptor.getValue());
    }

    @Test
    void shouldPassMissingSignatureToServiceAndReturnUnauthorized()
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
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Недействительная подпись webhook Tribute"
                ))
                .andExpect(jsonPath("$.path").value(PATH));
    }

    @Test
    void shouldPassMissingBodyToServiceAndReturnUnauthorized()
            throws Exception {
        given(webhookService.processWebhook(
                eq(new byte[0]),
                eq(SIGNATURE)
        )).willThrow(
                new InvalidTributeWebhookSignatureException()
        );

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("trbt-signature", SIGNATURE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Недействительная подпись webhook Tribute"
                ));
    }

    @Test
    void shouldReturnBadRequestForSignedInvalidPayload()
            throws Exception {
        given(webhookService.processWebhook(
                any(byte[].class),
                eq(SIGNATURE)
        )).willThrow(new InvalidTributeWebhookException());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("trbt-signature", SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Некорректные данные webhook Tribute"
                ));
    }

    @Test
    void shouldReturnNotFoundWhenPurchaseIntentIsMissing()
            throws Exception {
        given(webhookService.processWebhook(
                any(byte[].class),
                eq(SIGNATURE)
        )).willThrow(
                new SubscriptionPurchaseIntentNotFoundException()
        );

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("trbt-signature", SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Заявка на покупку подписки не найдена"
                ));
    }

    @Test
    void shouldRejectUnsupportedContentTypeBeforeService()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("trbt-signature", SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(webhookService);
    }

    @Test
    void shouldBeHiddenFromOpenApi() {
        assertNotNull(
                TributeWebhookController.class.getAnnotation(
                        Hidden.class
                )
        );
    }

    @Test
    void shouldRejectOversizedBodyBeforeCallingService() throws Exception {
        org.mockito.Mockito.when(properties.webhookMaxBodyBytes())
                .thenReturn(1024);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("trbt-signature", SIGNATURE)
                        .content(new byte[1025]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.path").value(PATH));

        verifyNoInteractions(webhookService);
    }

    @Test
    void shouldRejectMissingWebhookService() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TributeWebhookController(null, properties)
        );

        assertEquals(
                "Сервис webhook Tribute не должен быть null",
                exception.getMessage()
        );
    }
}
