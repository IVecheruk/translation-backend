package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCheckoutAttachmentCommand;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionPurchaseIntentCheckoutAttachmentService {

    private final SubscriptionPurchaseIntentRepository intentRepository;
    private final Clock clock;

    public SubscriptionPurchaseIntentCheckoutAttachmentService(
            SubscriptionPurchaseIntentRepository intentRepository,
            Clock clock
    ) {
        this.intentRepository = intentRepository;
        this.clock = clock;
    }

    @Transactional
    public void attach(
            SubscriptionPurchaseIntentCheckoutAttachmentCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда привязки checkout не должна быть null"
        );

        SubscriptionPurchaseIntent intent = intentRepository
                .findByIdAndUserIdForUpdate(
                        command.intentId(),
                        command.userId()
                )
                .orElseThrow(
                        SubscriptionPurchaseIntentNotFoundException::new
                );

        Instant now = clock.instant();

        intent.attachCheckout(
                command.externalCheckoutId(),
                now
        );
    }

    @Transactional
    public void abandon(UUID userId, UUID intentId) {
        SubscriptionPurchaseIntent intent = intentRepository
                .findByIdAndUserIdForUpdate(intentId, userId)
                .orElseThrow(SubscriptionPurchaseIntentNotFoundException::new);
        if (intent.getStatus()
                == com.translatelab.backend.payment.entity
                .SubscriptionPurchaseIntentStatus.PENDING) {
            intent.cancel();
        }
    }
}
