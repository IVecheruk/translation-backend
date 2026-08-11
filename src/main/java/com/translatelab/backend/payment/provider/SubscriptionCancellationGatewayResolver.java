package com.translatelab.backend.payment.provider;

import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SubscriptionCancellationGatewayResolver {

    private final Map<String, SubscriptionCancellationGateway> gateways;

    public SubscriptionCancellationGatewayResolver(
            List<SubscriptionCancellationGateway> gateways
    ) {
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(
                SubscriptionCancellationGateway::providerCode,
                Function.identity()
        ));
    }

    public SubscriptionCancellationGateway resolve(String provider) {
        SubscriptionCancellationGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw new PaymentProviderUnavailableException();
        }
        return gateway;
    }
}
