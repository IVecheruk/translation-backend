package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionRenewalCommandTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    private static final Instant PERIOD_END = Instant.parse(
            "2026-10-01T00:00:00Z"
    );

    @Test
    void shouldCreateCommandAndNormalizeExternalIds() {
        SubscriptionRenewalCommand command =
                new SubscriptionRenewalCommand(
                        "TRIBUTE",
                        "  event-123  ",
                        "  subscription-456  ",
                        PERIOD_START,
                        PERIOD_END
                );

        assertAll(
                () -> assertEquals("TRIBUTE", command.provider()),
                () -> assertEquals("event-123", command.externalEventId()),
                () -> assertEquals(
                        "subscription-456",
                        command.externalSubscriptionId()
                ),
                () -> assertEquals(PERIOD_START, command.newPeriodStart()),
                () -> assertEquals(PERIOD_END, command.newPeriodEnd())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_2",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderCodes(String provider) {
        SubscriptionRenewalCommand command = validCommand(
                provider,
                "event-123",
                "subscription-456",
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
                        "subscription-456",
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
                        "subscription-456",
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectExternalEventIdLongerThan255Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "e".repeat(256),
                        "subscription-456",
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
                        externalSubscriptionId,
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectExternalSubscriptionIdLongerThan255Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(
                        "TRIBUTE",
                        "event-123",
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
                        "subscription-456",
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
                        "subscription-456",
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
                        "subscription-456",
                        PERIOD_START,
                        periodEnd
                )
        );
    }

    private SubscriptionRenewalCommand validCommand(
            String provider,
            String externalEventId,
            String externalSubscriptionId,
            Instant periodStart,
            Instant periodEnd
    ) {
        return new SubscriptionRenewalCommand(
                provider,
                externalEventId,
                externalSubscriptionId,
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
