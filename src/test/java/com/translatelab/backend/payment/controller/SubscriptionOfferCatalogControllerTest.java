package com.translatelab.backend.payment.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.payment.dto.SubscriptionOfferResponse;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.service.SubscriptionOfferCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionOfferCatalogController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class SubscriptionOfferCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionOfferCatalogService catalogService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnPublicCatalogWithoutAuthentication() throws Exception {
        given(catalogService.getCatalog()).willReturn(List.of(
                new SubscriptionOfferResponse(
                        "BASIC",
                        "Basic",
                        49900,
                        "RUB",
                        BillingPeriod.MONTH
                ),
                new SubscriptionOfferResponse(
                        "PRO",
                        "Pro",
                        99900,
                        "RUB",
                        BillingPeriod.MONTH
                )
        ));

        mockMvc.perform(get("/api/subscription-offers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plan_code").value("BASIC"))
                .andExpect(jsonPath("$[0].plan_display_name")
                        .value("Basic"))
                .andExpect(jsonPath("$[0].price_minor").value(49900))
                .andExpect(jsonPath("$[0].currency").value("RUB"))
                .andExpect(jsonPath("$[0].billing_period")
                        .value("MONTH"))
                .andExpect(jsonPath("$[1].plan_code").value("PRO"))
                .andExpect(jsonPath("$[1].plan_display_name")
                        .value("Pro"))
                .andExpect(jsonPath("$[1].price_minor").value(99900))
                .andExpect(jsonPath("$[1].currency").value("RUB"))
                .andExpect(jsonPath("$[1].billing_period")
                        .value("MONTH"));

        verify(catalogService).getCatalog();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void shouldReturnEmptyJsonArrayWhenCatalogHasNoOffers()
            throws Exception {
        given(catalogService.getCatalog()).willReturn(List.of());

        mockMvc.perform(get("/api/subscription-offers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().json("[]"));

        verify(catalogService).getCatalog();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void shouldKeepPostRequestProtected() throws Exception {
        mockMvc.perform(post("/api/subscription-offers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/subscription-offers"));

        verifyNoInteractions(jwtDecoder, catalogService);
    }
}
