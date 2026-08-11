package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.exception.InvalidPaymentConfirmationException;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.exception.UserSubscriptionNotFoundException;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SubscriptionProviderLifecycleService {

    private final ProcessedPaymentEventRepository eventRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    public SubscriptionProviderLifecycleService(
            ProcessedPaymentEventRepository eventRepository,
            UserSubscriptionRepository subscriptionRepository
    ) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public boolean processRecurringCharge(
            String provider,
            String eventId,
            String externalOrderId,
            long amountMinor,
            String currency,
            BillingPeriod billingPeriod
    ) {
        if (!reserve(provider, eventId, "SUBSCRIPTION_CHARGE_SUCCEEDED")) {
            return false;
        }
        UserSubscription subscription = find(provider, externalOrderId);
        verifyCommercialSnapshot(
                subscription,
                amountMinor,
                currency,
                billingPeriod
        );
        Instant periodStart = subscription.getCurrentPeriodEnd();
        Instant periodEnd = addPeriod(periodStart, billingPeriod);
        subscription.applySuccessfulRecurringCharge(periodStart, periodEnd);
        return true;
    }

    @Transactional
    public boolean processChargeFailure(
            String provider,
            String eventId,
            String externalOrderId,
            long amountMinor,
            String currency,
            BillingPeriod billingPeriod
    ) {
        if (!reserve(provider, eventId, "SUBSCRIPTION_CHARGE_FAILED")) {
            return false;
        }
        UserSubscription subscription = find(provider, externalOrderId);
        verifyCommercialSnapshot(
                subscription,
                amountMinor,
                currency,
                billingPeriod
        );
        if (subscription.getStatus()
                == com.translatelab.backend.subscription.entity.SubscriptionStatus.ACTIVE) {
            subscription.markPastDue();
        }
        return true;
    }

    @Transactional
    public boolean processCancellation(
            String provider,
            String eventId,
            String externalOrderId
    ) {
        if (!reserve(provider, eventId, "SUBSCRIPTION_CANCELLATION_SCHEDULED")) {
            return false;
        }
        find(provider, externalOrderId).applyProviderCancellation();
        return true;
    }

    @Transactional
    public boolean processRevocation(
            String provider,
            String eventId,
            String externalOrderId,
            long amountMinor,
            String currency
    ) {
        if (!reserve(provider, eventId, "SUBSCRIPTION_REVOKED")) {
            return false;
        }
        UserSubscription subscription = find(provider, externalOrderId);
        verifyCommercialSnapshot(
                subscription,
                amountMinor,
                currency,
                BillingPeriod.MONTH
        );
        subscription.revokeByProvider();
        return true;
    }

    private boolean reserve(String provider, String eventId, String type) {
        int inserted = eventRepository.insertIfAbsent(
                UUID.randomUUID(), provider, eventId, type
        );
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException(
                    "Неожиданный результат регистрации платёжного события"
            );
        }
        return inserted == 1;
    }

    private UserSubscription find(String provider, String externalOrderId) {
        return subscriptionRepository.findByProviderAndExternalOrderIdForUpdate(
                provider,
                externalOrderId
        ).orElseThrow(UserSubscriptionNotFoundException::new);
    }

    private void verifyCommercialSnapshot(
            UserSubscription subscription,
            long amountMinor,
            String currency,
            BillingPeriod billingPeriod
    ) {
        if (!subscription.matchesCommercialSnapshot(
                amountMinor,
                currency,
                billingPeriod
        )) {
            throw new InvalidPaymentConfirmationException();
        }
    }

    private Instant addPeriod(Instant start, BillingPeriod billingPeriod) {
        return switch (billingPeriod) {
            case MONTH -> start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        };
    }
}
