package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.payment.dto.SubscriptionOfferResponse;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionOfferCatalogServiceTest {

    private static final String PROVIDER = "TRIBUTE";

    @Mock
    private PlanPaymentOfferRepository offerRepository;

    private SubscriptionOfferCatalogService service;

    @BeforeEach
    void setUp() {
        PaymentProperties paymentProperties = new PaymentProperties(
                Duration.ofMinutes(15),
                PROVIDER
        );
        service = new SubscriptionOfferCatalogService(
                offerRepository,
                paymentProperties
        );
    }

    @Test
    void shouldReturnMappedCatalogUsingConfiguredProvider() {
        PlanPaymentOffer basicOffer = offer(
                "BASIC_TRIBUTE_MONTH",
                "BASIC",
                "Basic",
                49900
        );
        PlanPaymentOffer proOffer = offer(
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Pro",
                99900
        );
        given(offerRepository.findActiveCatalog(
                PROVIDER,
                BillingPeriod.MONTH
        )).willReturn(List.of(basicOffer, proOffer));

        List<SubscriptionOfferResponse> catalog = service.getCatalog();

        assertEquals(
                List.of(
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
                ),
                catalog
        );
        verify(offerRepository).findActiveCatalog(
                PROVIDER,
                BillingPeriod.MONTH
        );
        verifyNoMoreInteractions(offerRepository);
    }

    @Test
    void shouldReturnEmptyCatalogWhenRepositoryHasNoOffers() {
        given(offerRepository.findActiveCatalog(
                PROVIDER,
                BillingPeriod.MONTH
        )).willReturn(List.of());

        List<SubscriptionOfferResponse> catalog = service.getCatalog();

        assertTrue(catalog.isEmpty());
        verify(offerRepository).findActiveCatalog(
                PROVIDER,
                BillingPeriod.MONTH
        );
    }

    @Test
    void shouldReturnUnmodifiableCatalog() {
        PlanPaymentOffer offer = offer(
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Pro",
                99900
        );
        given(offerRepository.findActiveCatalog(
                PROVIDER,
                BillingPeriod.MONTH
        )).willReturn(List.of(offer));

        List<SubscriptionOfferResponse> catalog = service.getCatalog();

        assertThrows(UnsupportedOperationException.class, catalog::clear);
    }

    @Test
    void shouldDeclareReadOnlyTransaction() throws NoSuchMethodException {
        Method method = SubscriptionOfferCatalogService.class
                .getDeclaredMethod("getCatalog");

        Transactional transactional = method.getAnnotation(
                Transactional.class
        );

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }

    private PlanPaymentOffer offer(
            String offerCode,
            String planCode,
            String planDisplayName,
            long priceMinor
    ) {
        SubscriptionPlan plan = new SubscriptionPlan(
                planCode,
                planDisplayName
        );

        return new PlanPaymentOffer(
                offerCode,
                plan,
                PROVIDER,
                priceMinor,
                "RUB",
                BillingPeriod.MONTH,
                "external-" + planCode.toLowerCase()
        );
    }
}
