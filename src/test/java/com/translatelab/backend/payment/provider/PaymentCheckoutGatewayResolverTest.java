package com.translatelab.backend.payment.provider;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCheckoutGatewayResolverTest {

    private static final String SAFE_UNAVAILABLE_MESSAGE =
            "Платёжный сервис временно недоступен";

    @Test
    void shouldResolveRegisteredGatewaysByExactProviderCode() {
        PaymentCheckoutGateway tribute = gateway("TRIBUTE");
        PaymentCheckoutGateway other = gateway("OTHER_PROVIDER");
        PaymentCheckoutGatewayResolver resolver =
                new PaymentCheckoutGatewayResolver(
                        List.of(tribute, other)
                );

        assertSame(tribute, resolver.resolve("TRIBUTE"));
        assertSame(other, resolver.resolve("OTHER_PROVIDER"));
    }

    @Test
    void shouldAllowEmptyGatewayList() {
        PaymentCheckoutGatewayResolver resolver =
                new PaymentCheckoutGatewayResolver(List.of());

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> resolver.resolve("TRIBUTE")
        );

        assertEquals(
                SAFE_UNAVAILABLE_MESSAGE,
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "TRIBUTE PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidRequestedProviderCode(String providerCode) {
        PaymentCheckoutGatewayResolver resolver =
                new PaymentCheckoutGatewayResolver(
                        List.of(gateway("TRIBUTE"))
                );

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> resolver.resolve(providerCode)
        );

        assertEquals(
                SAFE_UNAVAILABLE_MESSAGE,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnregisteredValidProviderCode() {
        PaymentCheckoutGatewayResolver resolver =
                new PaymentCheckoutGatewayResolver(
                        List.of(gateway("TRIBUTE"))
                );

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> resolver.resolve("OTHER_PROVIDER")
        );

        assertEquals(
                SAFE_UNAVAILABLE_MESSAGE,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullGatewayList() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new PaymentCheckoutGatewayResolver(null)
        );

        assertEquals(
                "Список платёжных gateway не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullGatewayElement() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new PaymentCheckoutGatewayResolver(
                        Collections.singletonList(null)
                )
        );

        assertEquals(
                "Список платёжных gateway не должен содержать null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "TRIBUTE PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidGatewayProviderCode(String providerCode) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new PaymentCheckoutGatewayResolver(
                        List.of(gateway(providerCode))
                )
        );

        assertEquals(
                "Gateway вернул некорректный код провайдера",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptProviderCodeAtMaximumLength() {
        String providerCode = "A" + "1".repeat(31);
        PaymentCheckoutGateway gateway = gateway(providerCode);
        PaymentCheckoutGatewayResolver resolver =
                new PaymentCheckoutGatewayResolver(List.of(gateway));

        assertSame(gateway, resolver.resolve(providerCode));
    }

    @Test
    void shouldRejectDuplicateGatewayProviderCode() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new PaymentCheckoutGatewayResolver(List.of(
                        gateway("TRIBUTE"),
                        gateway("TRIBUTE")
                ))
        );

        assertEquals(
                "Зарегистрировано несколько gateway "
                        + "для провайдера TRIBUTE",
                exception.getMessage()
        );
    }

    @Test
    void shouldKeepImmutableSnapshotOfGatewayList() {
        PaymentCheckoutGateway tribute = gateway("TRIBUTE");
        List<PaymentCheckoutGateway> gateways = new ArrayList<>();
        gateways.add(tribute);
        PaymentCheckoutGatewayResolver resolver =
                new PaymentCheckoutGatewayResolver(gateways);

        gateways.clear();
        gateways.add(gateway("OTHER_PROVIDER"));

        assertSame(tribute, resolver.resolve("TRIBUTE"));
        assertThrows(
                PaymentProviderUnavailableException.class,
                () -> resolver.resolve("OTHER_PROVIDER")
        );
    }

    private PaymentCheckoutGateway gateway(String providerCode) {
        return new TestPaymentCheckoutGateway(providerCode);
    }

    private record TestPaymentCheckoutGateway(
            String providerCode
    ) implements PaymentCheckoutGateway {

        @Override
        public PaymentCheckoutResult createCheckout(
                PaymentCheckoutCreationCommand command
        ) {
            throw new AssertionError(
                    "Resolver не должен создавать checkout"
            );
        }
    }
}
