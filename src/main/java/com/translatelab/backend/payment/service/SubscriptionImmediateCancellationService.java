package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionImmediateCancellationCommand;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.exception.UserSubscriptionNotFoundException;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionImmediateCancellationService {

    private static final String EVENT_TYPE =
            "SUBSCRIPTION_CANCELED_IMMEDIATELY";

    private final ProcessedPaymentEventRepository paymentEventRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public SubscriptionImmediateCancellationService(
            ProcessedPaymentEventRepository paymentEventRepository,
            UserSubscriptionRepository userSubscriptionRepository
    ) {
        this.paymentEventRepository = paymentEventRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
    }

    @Transactional
    public boolean processImmediateCancellation(
            SubscriptionImmediateCancellationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда немедленной отмены не должна быть null"
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

        subscription.cancelImmediately();

        return true;
    }
}