package com.translatelab.backend.payment.service;

import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class SubscriptionExpirationReconciliationService {

    private final UserSubscriptionRepository repository;
    private final Clock clock;

    public SubscriptionExpirationReconciliationService(
            UserSubscriptionRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int reconcileBatch(int batchSize) {
        Instant now = clock.instant();
        List<UserSubscription> subscriptions = repository
                .findExpiredLiveForUpdate(now, PageRequest.of(0, batchSize));
        subscriptions.forEach(subscription -> subscription.expire(now));
        return subscriptions.size();
    }
}
