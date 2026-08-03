package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationResult;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.exception.SubscriptionPlanNotFoundException;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class SubscriptionPurchaseIntentCreationService {

    private static final String FREE_PLAN_CODE = "FREE";

    private final SubscriptionPurchaseIntentRepository intentRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public SubscriptionPurchaseIntentCreationService(
            SubscriptionPurchaseIntentRepository intentRepository,
            UserRepository userRepository,
            SubscriptionPlanRepository planRepository,
            PaymentProperties paymentProperties,
            Clock clock
    ) {
        this.intentRepository = intentRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionPurchaseIntentCreationResult create(
        SubscriptionPurchaseIntentCreationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда создания заявки не должна быть null"
        );

        User user = userRepository.findById(command.userId())
                .orElseThrow(UserNotFoundException::new);

        SubscriptionPlan plan = planRepository
                .findById(command.planCode())
                .filter(SubscriptionPlan::isActive)
                .filter(candidate ->
                        !FREE_PLAN_CODE.equals(candidate.getCode())
                )
                .orElseThrow(SubscriptionPlanNotFoundException::new);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(
                paymentProperties.purchaseIntentTtl()
        );

        SubscriptionPurchaseIntent intent =
                SubscriptionPurchaseIntent.pending(
                        user,
                        plan,
                        command.provider(),
                        now,
                        expiresAt
                );

        SubscriptionPurchaseIntent savedIntent =
                intentRepository.saveAndFlush(intent);

        return new SubscriptionPurchaseIntentCreationResult(
                savedIntent.getId(),
                savedIntent.getProvider(),
                savedIntent.getExpiresAt()
        );
    }
}