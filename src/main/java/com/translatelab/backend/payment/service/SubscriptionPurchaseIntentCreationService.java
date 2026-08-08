package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchasePreparationResult;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.exception.PlanPaymentOfferNotFoundException;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseConflictException;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
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
    private final PlanPaymentOfferRepository offerRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public SubscriptionPurchaseIntentCreationService(
            SubscriptionPurchaseIntentRepository intentRepository,
            UserRepository userRepository,
            PlanPaymentOfferRepository offerRepository,
            UserSubscriptionRepository subscriptionRepository,
            PaymentProperties paymentProperties,
            Clock clock
    ) {
        this.intentRepository = intentRepository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionPurchasePreparationResult prepare(
            SubscriptionPurchaseIntentCreationCommand command
    ) {
        return createPreparation(command);
    }

    private SubscriptionPurchasePreparationResult createPreparation(
            SubscriptionPurchaseIntentCreationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда создания заявки не должна быть null"
        );

        User user = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(UserNotFoundException::new);

        PlanPaymentOffer offer = offerRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                command.planCode(),
                command.provider(),
                BillingPeriod.MONTH
        )
                .orElseThrow(PlanPaymentOfferNotFoundException::new);

        SubscriptionPlan plan = offer.getPlan();

        if (FREE_PLAN_CODE.equals(plan.getCode())) {
            throw new PlanPaymentOfferNotFoundException();
        }

        Instant now = clock.instant();
        reconcilePurchaseState(command, now);

        Instant expiresAt = now.plus(
                paymentProperties.purchaseIntentTtl()
        );

        SubscriptionPurchaseIntent intent =
                SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        now,
                        expiresAt
                );

        SubscriptionPurchaseIntent savedIntent =
                intentRepository.saveAndFlush(intent);

        SubscriptionPurchaseIntentCreationResult intentCreationResult =
                new SubscriptionPurchaseIntentCreationResult(
                        savedIntent.getId(),
                        savedIntent.getProvider(),
                        savedIntent.getExpiresAt()
                );

        PaymentCheckoutCreationCommand checkoutCommand =
                new PaymentCheckoutCreationCommand(
                        savedIntent.getId(),
                        offer.getCode(),
                        plan.getCode(),
                        plan.getDisplayName(),
                        intent.getPriceMinor(),
                        intent.getCurrency(),
                        intent.getBillingPeriod(),
                        intent.getExternalProductId(),
                        savedIntent.getExpiresAt()
                );

        return new SubscriptionPurchasePreparationResult(
                intentCreationResult,
                checkoutCommand
        );
    }

    private void reconcilePurchaseState(
            SubscriptionPurchaseIntentCreationCommand command,
            Instant now
    ) {
        subscriptionRepository.findLiveByUserIdForUpdate(command.userId())
                .ifPresent(subscription -> reconcileSubscription(subscription, now));

        intentRepository.findPendingByUserIdForUpdate(command.userId())
                .ifPresent(intent -> reconcileIntent(intent, now));
    }

    private void reconcileSubscription(
            UserSubscription subscription,
            Instant now
    ) {
        if (!now.isBefore(subscription.getCurrentPeriodEnd())) {
            subscription.expire(now);
            return;
        }

        throw new SubscriptionPurchaseConflictException();
    }

    private void reconcileIntent(
            SubscriptionPurchaseIntent intent,
            Instant now
    ) {
        if (!now.isBefore(intent.getExpiresAt())) {
            intent.expire(now);
            return;
        }

        throw new SubscriptionPurchaseConflictException();
    }
}
