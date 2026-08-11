package com.translatelab.backend.subscription.service;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.exception.SubscriptionPlanNotFoundException;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.subscription.dto.AccountSubscriptionResponse;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AccountSubscriptionService {

    private static final String FREE_PLAN_CODE = "FREE";

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final Clock clock;

    public AccountSubscriptionService(
            UserRepository userRepository,
            UserSubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountSubscriptionResponse getCurrent(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }
        return subscriptionRepository.findCurrentByUserIdAt(
                userId,
                clock.instant()
        ).map(this::toResponse).orElseGet(this::freeResponse);
    }

    private AccountSubscriptionResponse toResponse(UserSubscription subscription) {
        return new AccountSubscriptionResponse(
                subscription.getPlan().getCode(),
                subscription.getPlan().getDisplayName(),
                subscription.getStatus().name(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd()
        );
    }

    private AccountSubscriptionResponse freeResponse() {
        SubscriptionPlan plan = planRepository.findById(FREE_PLAN_CODE)
                .orElseThrow(SubscriptionPlanNotFoundException::new);
        return new AccountSubscriptionResponse(
                plan.getCode(),
                plan.getDisplayName(),
                "FREE",
                null,
                null,
                false
        );
    }
}
