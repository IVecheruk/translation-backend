package com.translatelab.backend.payment.dto;

import com.translatelab.backend.payment.entity.BillingPeriod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPurchaseCompletionCommandTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    private static final Instant PERIOD_END = Instant.parse(
            "2026-10-01T00:00:00Z"
    );

    @Test
    void shouldCreateCommandAndNormalizeExternalIds() {
        SubscriptionPurchaseCompletionCommand command = validCommand(
                "TRIBUTE",
                "  event-123  ",
                "  checkout-456  ",
                "  customer-789  ",
                "  subscription-012  ",
                PERIOD_START,
                PERIOD_END
        );

        assertAll(
                () -> assertEquals("TRIBUTE", command.provider()),
                () -> assertEquals("event-123", command.externalEventId()),
                () -> assertEquals(
                        "checkout-456",
                        command.externalCheckoutId()
                ),
                () -> assertEquals(
                        "customer-789",
                        command.externalCustomerId()
                ),
                () -> assertEquals(
                        "subscription-012",
                        command.externalSubscriptionId()
                ),
                () -> assertEquals(PERIOD_START, command.periodStart()),
                () -> assertEquals(PERIOD_END, command.periodEnd())
        );
    }

    @Test
    void shouldAllowMissingExternalCustomerId() {
        SubscriptionPurchaseCompletionCommand command = validCommand(
                "TRIBUTE",
                "event-123",
                "checkout-456",
                null,
                "subscription-012",
                PERIOD_START,
                PERIOD_END
        );

        assertNull(command.externalCustomerId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_2",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderCodes(String provider) {
        SubscriptionPurchaseCompletionCommand command = validCommand(
                provider,
                "event-123",
                "checkout-456",
                "customer-789",
                "subscription-012",
                PERIOD_START,
                PERIOD_END
        );

        assertEquals(provider, command.provider());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidProviderCodes(String provider) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        provider,
                        "event-123",
                        "checkout-456",
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingExternalEventId(String externalEventId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        externalEventId,
                        "checkout-456",
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingExternalCheckoutId(String externalCheckoutId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        externalCheckoutId,
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingExternalSubscriptionId(
            String externalSubscriptionId
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        "customer-789",
                        externalSubscriptionId,
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void shouldRejectBlankExternalCustomerId(String externalCustomerId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        externalCustomerId,
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldAcceptExternalIdsAtMaximumLengthAfterNormalization() {
        String maximumId = "x".repeat(255);

        SubscriptionPurchaseCompletionCommand command = validCommand(
                "TRIBUTE",
                "  " + maximumId + "  ",
                "  " + maximumId + "  ",
                "  " + maximumId + "  ",
                "  " + maximumId + "  ",
                PERIOD_START,
                PERIOD_END
        );

        assertAll(
                () -> assertEquals(maximumId, command.externalEventId()),
                () -> assertEquals(maximumId, command.externalCheckoutId()),
                () -> assertEquals(maximumId, command.externalCustomerId()),
                () -> assertEquals(
                        maximumId,
                        command.externalSubscriptionId()
                )
        );
    }

    @Test
    void shouldRejectExternalEventIdLongerThanMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "e".repeat(256),
                        "checkout-456",
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectExternalCheckoutIdLongerThanMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "c".repeat(256),
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectExternalCustomerIdLongerThanMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        "c".repeat(256),
                        "subscription-012",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectExternalSubscriptionIdLongerThanMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        "customer-789",
                        "s".repeat(256),
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectNullPeriodStart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        "customer-789",
                        "subscription-012",
                        null,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectNullPeriodEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        null
                )
        );
    }

    @ParameterizedTest
    @MethodSource("nonLaterPeriodEnds")
    void shouldRejectPeriodEndThatIsNotAfterStart(Instant periodEnd) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
                        "checkout-456",
                        "customer-789",
                        "subscription-012",
                        PERIOD_START,
                        periodEnd
                )
        );
    }

    private SubscriptionPurchaseCompletionCommand validCommand(
            String provider,
            String externalEventId,
            String externalCheckoutId,
            String externalCustomerId,
            String externalSubscriptionId,
            Instant periodStart,
            Instant periodEnd
    ) {
        return new SubscriptionPurchaseCompletionCommand(
                provider,
                externalEventId,
                externalCheckoutId,
                externalSubscriptionId,
                externalCustomerId,
                externalSubscriptionId,
                1,
                "RUB",
                BillingPeriod.MONTH,
                null,
                periodStart,
                periodEnd
        );
    }

    private static Stream<Instant> nonLaterPeriodEnds() {
        return Stream.of(
                PERIOD_START,
                PERIOD_START.minusSeconds(1)
        );
    }
}
