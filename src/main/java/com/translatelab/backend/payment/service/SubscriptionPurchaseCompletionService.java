package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.exception.InvalidPaymentConfirmationException;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.repository.UserRepository;
import com.translatelab.backend.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionPurchaseCompletionService {

    private static final String EVENT_TYPE =
            "SUBSCRIPTION_PURCHASE_COMPLETED";

    private final ProcessedPaymentEventRepository paymentEventRepository;
    private final SubscriptionPurchaseIntentRepository intentRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SubscriptionPurchaseCompletionService(
            ProcessedPaymentEventRepository paymentEventRepository,
            SubscriptionPurchaseIntentRepository intentRepository,
            UserSubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.paymentEventRepository = paymentEventRepository;
        this.intentRepository = intentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean processCompletion(
            SubscriptionPurchaseCompletionCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда завершения покупки не должна быть null"
        );

        int insertedRows = paymentEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                command.provider(),
                command.externalEventId(),
                EVENT_TYPE
        );

        if (insertedRows == 0) {
            return false;
        }

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "Неожиданный результат регистрации платёжного события"
            );
        }

        SubscriptionPurchaseIntent candidate = intentRepository
                .findByProviderAndExternalCheckoutId(
                        command.provider(),
                        command.externalCheckoutId()
                )
                .orElseThrow(
                        SubscriptionPurchaseIntentNotFoundException::new
                );

        UUID userId = candidate.getUser().getId();
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);

        SubscriptionPurchaseIntent intent = intentRepository
                .findByProviderAndExternalCheckoutIdForUpdate(
                        command.provider(),
                        command.externalCheckoutId()
                )
                .orElseThrow(
                        SubscriptionPurchaseIntentNotFoundException::new
                );

        Instant now = clock.instant();

        if (!intent.matchesCommercialSnapshot(
                command.paidAmountMinor(),
                command.paidCurrency(),
                command.paidBillingPeriod(),
                command.paidExternalProductId()
        )) {
            throw new InvalidPaymentConfirmationException();
        }

        UserSubscription liveSubscription = subscriptionRepository
                .findLiveByUserIdForUpdate(userId)
                .orElse(null);
        if (liveSubscription != null) {
            if (now.isBefore(liveSubscription.getCurrentPeriodEnd())) {
                throw new com.translatelab.backend.payment.exception
                        .SubscriptionPurchaseConflictException();
            }
            liveSubscription.expire(now);
        }

        UserSubscription subscription =
                UserSubscription.providerManagedPurchase(
                        intent.getUser(),
                        intent.getPlan(),
                        command.periodStart(),
                        command.periodEnd(),
                        command.provider(),
                        command.externalCustomerId(),
                        command.externalOrderId(),
                        command.externalSubscriptionId(),
                        command.paidAmountMinor(),
                        command.paidCurrency(),
                        command.paidBillingPeriod(),
                        command.paidExternalProductId()
                );

        intent.consume(now);
        subscriptionRepository.saveAndFlush(subscription);

        return true;
    }
}
