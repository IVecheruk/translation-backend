package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.exception.InvalidPaymentConfirmationException;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubscriptionPurchaseFailureService {

    private final ProcessedPaymentEventRepository eventRepository;
    private final SubscriptionPurchaseIntentRepository intentRepository;

    public SubscriptionPurchaseFailureService(
            ProcessedPaymentEventRepository eventRepository,
            SubscriptionPurchaseIntentRepository intentRepository
    ) {
        this.eventRepository = eventRepository;
        this.intentRepository = intentRepository;
    }

    @Transactional
    public boolean processFailure(
            String provider,
            String eventId,
            String externalCheckoutId,
            long amountMinor,
            String currency
    ) {
        int inserted = eventRepository.insertIfAbsent(
                UUID.randomUUID(),
                provider,
                eventId,
                "SUBSCRIPTION_PURCHASE_FAILED"
        );
        if (inserted == 0) {
            return false;
        }
        if (inserted != 1) {
            throw new IllegalStateException(
                    "Неожиданный результат регистрации платёжного события"
            );
        }
        var intent = intentRepository
                .findByProviderAndExternalCheckoutIdForUpdate(
                        provider,
                        externalCheckoutId
                )
                .orElseThrow(SubscriptionPurchaseIntentNotFoundException::new);
        if (!intent.hasCommercialSnapshot()
                || intent.getPriceMinor() != amountMinor
                || !intent.getCurrency().equals(currency)) {
            throw new InvalidPaymentConfirmationException();
        }
        intent.cancel();
        return true;
    }
}
