package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.provider.tribute.dto.TributeCreateOrderRequest;
import com.translatelab.backend.payment.provider.tribute.dto.TributeCreateOrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TributeCheckoutMapperTest {

    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final Instant EXPIRES_AT = Instant.parse(
            "2026-09-01T00:30:00Z"
    );
    private static final URI PAYMENT_URL = URI.create(
            "https://web.tribute.tg/shop/pay/"
                    + "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final URI WEBAPP_PAYMENT_URL = URI.create(
            "https://t.me/tribute/app?startapp=b2RK4mN"
    );

    private final TributeCheckoutMapper mapper =
            new TributeCheckoutMapper();

    @Test
    void shouldMapCompleteInternalCommandToTributeRequest() {
        PaymentCheckoutCreationCommand command = command(
                "RUB",
                "tribute-product-binding"
        );

        TributeCreateOrderRequest request =
                mapper.toCreateOrderRequest(command);

        assertAll(
                () -> assertEquals(49_900L, request.amount()),
                () -> assertEquals("rub", request.currency()),
                () -> assertEquals(
                        "Профессиональный",
                        request.title()
                ),
                () -> assertEquals(
                        "Подписка «Профессиональный» на один месяц",
                        request.description()
                ),
                () -> assertEquals(INTENT_ID, request.customerId()),
                () -> assertEquals("monthly", request.period())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "EUR, eur",
            "RUB, rub",
            "USD, usd"
    })
    void shouldMapEverySupportedCurrency(
            String internalCurrency,
            String tributeCurrency
    ) {
        TributeCreateOrderRequest request = mapper.toCreateOrderRequest(
                command(internalCurrency, null)
        );

        assertEquals(tributeCurrency, request.currency());
    }

    @Test
    void shouldRejectCurrencyUnsupportedByTributeBeforeNetworkCall() {
        PaymentCheckoutCreationCommand command = command("GBP", null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toCreateOrderRequest(command)
        );

        assertEquals(
                "Валюта платёжного предложения "
                        + "не поддерживается Tribute",
                exception.getMessage()
        );
    }

    @Test
    void shouldIgnoreExternalProductBindingForDynamicShopOrder() {
        TributeCreateOrderRequest withoutBinding =
                mapper.toCreateOrderRequest(command("RUB", null));
        TributeCreateOrderRequest withBinding =
                mapper.toCreateOrderRequest(
                        command("RUB", "legacy-product-id")
                );

        assertEquals(withoutBinding, withBinding);
    }

    @Test
    void shouldRejectNullCreationCommand() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> mapper.toCreateOrderRequest(null)
        );

        assertEquals(
                "Команда создания checkout не должна быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldMapOrderIdAndTelegramUrlToProviderNeutralResult() {
        TributeCreateOrderResponse response = validResponse();

        PaymentCheckoutResult result = mapper.toCheckoutResult(response);

        assertAll(
                () -> assertEquals(
                        ORDER_ID.toString(),
                        result.externalCheckoutId()
                ),
                () -> assertEquals(
                        WEBAPP_PAYMENT_URL,
                        result.redirectUrl()
                ),
                () -> assertFalse(
                        PAYMENT_URL.equals(result.redirectUrl())
                )
        );
    }

    @Test
    void shouldRejectNullTributeResponse() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> mapper.toCheckoutResult(null)
        );

        assertEquals(
                "Ответ Tribute не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectProviderRedirectOutsideOfficialTelegramApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toCheckoutResult(
                        new TributeCreateOrderResponse(
                                ORDER_ID,
                                PAYMENT_URL,
                                URI.create("https://attacker.example/pay")
                        )
                )
        );
    }

    @Test
    void shouldBeAvailableForConstructorInjection() {
        assertTrue(
                TributeCheckoutMapper.class
                        .isAnnotationPresent(Component.class)
        );
    }

    private PaymentCheckoutCreationCommand command(
            String currency,
            String externalProductId
    ) {
        return new PaymentCheckoutCreationCommand(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                49_900L,
                currency,
                BillingPeriod.MONTH,
                externalProductId,
                EXPIRES_AT
        );
    }

    private TributeCreateOrderResponse validResponse() {
        return new TributeCreateOrderResponse(
                ORDER_ID,
                PAYMENT_URL,
                WEBAPP_PAYMENT_URL
        );
    }
}
