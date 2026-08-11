package com.translatelab.backend.subscription.service;

import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
import com.translatelab.backend.subscription.exception.UserSubscriptionNotFoundException;
import com.translatelab.backend.subscription.exception.SubscriptionCancellationNotSupportedException;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountSubscriptionCancellationStateService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public AccountSubscriptionCancellationStateService(
            UserRepository userRepository,
            UserSubscriptionRepository subscriptionRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Transactional
    public CancellationPreparation prepare(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);
        UserSubscription subscription = subscriptionRepository
                .findLiveByUserIdForUpdate(userId)
                .orElseThrow(UserSubscriptionNotFoundException::new);
        Instant now = clock.instant();
        if (!now.isBefore(subscription.getCurrentPeriodEnd())) {
            subscription.expire(now);
            throw new UserSubscriptionNotFoundException();
        }
        if (subscription.getProvider() == null
                || subscription.getExternalOrderId() == null) {
            throw new SubscriptionCancellationNotSupportedException();
        }
        return new CancellationPreparation(
                subscription.getId(),
                subscription.getProvider(),
                subscription.getExternalOrderId(),
                subscription.getStatus() == SubscriptionStatus.ACTIVE
                        ? subscription.getCurrentPeriodEnd()
                        : now,
                subscription.isCancelAtPeriodEnd(),
                subscription.getStatus()
        );
    }

    @Transactional
    public void confirm(UUID userId, UUID subscriptionId) {
        UserSubscription subscription = subscriptionRepository
                .findLiveByUserIdForUpdate(userId)
                .filter(current -> current.getId().equals(subscriptionId))
                .orElseThrow(UserSubscriptionNotFoundException::new);
        subscription.applyProviderCancellation();
    }

    public record CancellationPreparation(
            UUID subscriptionId,
            String provider,
            String externalOrderId,
            Instant effectiveAt,
            boolean alreadyScheduled,
            SubscriptionStatus status
    ) {}
}
