package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.payment.dto.SubscriptionOfferResponse;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubscriptionOfferCatalogService {

    private final PlanPaymentOfferRepository offerRepository;
    private final PaymentProperties paymentProperties;

    public SubscriptionOfferCatalogService(
            PlanPaymentOfferRepository offerRepository,
            PaymentProperties paymentProperties
    ) {
        this.offerRepository = offerRepository;
        this.paymentProperties = paymentProperties;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionOfferResponse> getCatalog() {
        String provider = paymentProperties.provider();

        return offerRepository
                .findActiveCatalog(provider, BillingPeriod.MONTH)
                .stream()
                .map(SubscriptionOfferCatalogService::toResponse)
                .toList();
    }

    private static SubscriptionOfferResponse toResponse(
            PlanPaymentOffer offer
    ) {
        SubscriptionPlan plan = offer.getPlan();

        return new SubscriptionOfferResponse(
                plan.getCode(),
                plan.getDisplayName(),
                offer.getPriceMinor(),
                offer.getCurrency(),
                offer.getBillingPeriod()
        );
    }
}
