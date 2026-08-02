package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionExpirationCommand;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.exception.UserSubscriptionNotFoundException;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionExpirationService {

    private static final String EVENT_TYPE =
            "SUBSCRIPTION_EXPIRED";

    private final ProcessedPaymentEventRepository paymentEventRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final Clock clock;

    public SubscriptionExpirationService(
            ProcessedPaymentEventRepository paymentEventRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            Clock clock
    ) {
        this.paymentEventRepository = paymentEventRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean processExpiration(
            SubscriptionExpirationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда истечения подписки не должна быть null"
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

        UserSubscription subscription = userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        command.provider(),
                        command.externalSubscriptionId()
                )
                .orElseThrow(
                        UserSubscriptionNotFoundException::new
                );

        subscription.expire(clock.instant());

        return true;
    }
}