package com.translatelab.backend.payment.provider;

import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class PaymentCheckoutGatewayResolver {

    private static final Pattern PROVIDER_CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    private final Map<String, PaymentCheckoutGateway> gatewaysByProvider;

    public PaymentCheckoutGatewayResolver(
            List<PaymentCheckoutGateway> gateways
    ) {
        Objects.requireNonNull(
                gateways,
                "Список платёжных gateway не должен быть null"
        );

        Map<String, PaymentCheckoutGateway> indexedGateways =
                new LinkedHashMap<>();

        for (PaymentCheckoutGateway gateway : gateways) {
            if (gateway == null) {
                throw new IllegalStateException(
                        "Список платёжных gateway "
                                + "не должен содержать null"
                );
            }

            String providerCode = validateGatewayProviderCode(
                    gateway.providerCode()
            );

            PaymentCheckoutGateway previous =
                    indexedGateways.putIfAbsent(
                            providerCode,
                            gateway
                    );

            if (previous != null) {
                throw new IllegalStateException(
                        "Зарегистрировано несколько gateway "
                                + "для провайдера " + providerCode
                );
            }
        }

        this.gatewaysByProvider = Map.copyOf(indexedGateways);
    }

    public PaymentCheckoutGateway resolve(String providerCode) {
        if (providerCode == null
                || !PROVIDER_CODE_PATTERN
                .matcher(providerCode)
                .matches()) {
            throw new PaymentProviderUnavailableException();
        }

        PaymentCheckoutGateway gateway =
                gatewaysByProvider.get(providerCode);

        if (gateway == null) {
            throw new PaymentProviderUnavailableException();
        }

        return gateway;
    }

    private static String validateGatewayProviderCode(
            String providerCode
    ) {
        if (providerCode == null
                || !PROVIDER_CODE_PATTERN
                .matcher(providerCode)
                .matches()) {
            throw new IllegalStateException(
                    "Gateway вернул некорректный код провайдера"
            );
        }

        return providerCode;
    }
}
