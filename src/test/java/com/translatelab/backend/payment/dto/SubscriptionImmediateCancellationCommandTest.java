package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionImmediateCancellationCommandTest {

    @Test
    void shouldCreateCommandAndNormalizeExternalIds() {
        SubscriptionImmediateCancellationCommand command =
                new SubscriptionImmediateCancellationCommand(
                        "TRIBUTE",
                        "  event-123  ",
                        "  subscription-456  "
                );

        assertAll(
                () -> assertEquals("TRIBUTE", command.provider()),
                () -> assertEquals("event-123", command.externalEventId()),
                () -> assertEquals(
                        "subscription-456",
                        command.externalSubscriptionId()
                )
        );
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
    void shouldRejectInvalidProvider(String provider) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionImmediateCancellationCommand(
                        provider,
                        "event-123",
                        "subscription-456"
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingExternalEventId(String externalEventId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionImmediateCancellationCommand(
                        "TRIBUTE",
                        externalEventId,
                        "subscription-456"
                )
        );
    }

    @Test
    void shouldRejectExternalEventIdLongerThan255Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionImmediateCancellationCommand(
                        "TRIBUTE",
                        "e".repeat(256),
                        "subscription-456"
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
                () -> new SubscriptionImmediateCancellationCommand(
                        "TRIBUTE",
                        "event-123",
                        externalSubscriptionId
                )
        );
    }

    @Test
    void shouldRejectExternalSubscriptionIdLongerThan255Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionImmediateCancellationCommand(
                        "TRIBUTE",
                        "event-123",
                        "s".repeat(256)
                )
        );
    }
}
