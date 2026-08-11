package com.translatelab.backend.subscription.service;

import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import com.translatelab.backend.payment.provider.SubscriptionCancellationGateway;
import com.translatelab.backend.payment.provider.SubscriptionCancellationGatewayResolver;
import com.translatelab.backend.subscription.dto.SubscriptionCancellationResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountSubscriptionCancellationService {

    private final AccountSubscriptionCancellationStateService stateService;
    private final SubscriptionCancellationGatewayResolver gatewayResolver;

    public AccountSubscriptionCancellationService(
            AccountSubscriptionCancellationStateService stateService,
            SubscriptionCancellationGatewayResolver gatewayResolver
    ) {
        this.stateService = stateService;
        this.gatewayResolver = gatewayResolver;
    }

    public SubscriptionCancellationResponse cancel(UUID userId) {
        var preparation = stateService.prepare(userId);
        if (!preparation.alreadyScheduled()) {
            SubscriptionCancellationGateway gateway = gatewayResolver.resolve(
                    preparation.provider()
            );
            if (!gateway.requestCancellation(preparation.externalOrderId())) {
                throw new PaymentProviderUnavailableException();
            }
            stateService.confirm(userId, preparation.subscriptionId());
        }
        return new SubscriptionCancellationResponse(
                preparation.status()
                        == com.translatelab.backend.subscription.entity
                        .SubscriptionStatus.ACTIVE,
                preparation.effectiveAt()
        );
    }
}
