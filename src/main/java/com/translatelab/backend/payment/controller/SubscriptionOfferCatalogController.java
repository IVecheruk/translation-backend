package com.translatelab.backend.payment.controller;

import com.translatelab.backend.payment.dto.SubscriptionOfferResponse;
import com.translatelab.backend.payment.service.SubscriptionOfferCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscription-offers")
public class SubscriptionOfferCatalogController {

    private final SubscriptionOfferCatalogService catalogService;

    public SubscriptionOfferCatalogController(
            SubscriptionOfferCatalogService catalogService
    ) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<SubscriptionOfferResponse> getCatalog() {
        return catalogService.getCatalog();
    }
}