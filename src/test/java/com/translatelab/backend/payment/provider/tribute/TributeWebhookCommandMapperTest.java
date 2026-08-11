package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TributeWebhookCommandMapperTest {

    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final String EXTERNAL_ORDER_ID =
            ORDER_ID.toString();
    private static final Instant SENT_AT = Instant.parse(
            "2026-09-01T00:00:01Z"
    );

    private final TributeWebhookCommandMapper mapper =
            new TributeWebhookCommandMapper();

    @Test
    void shouldMapInitialPurchaseToExactProviderIndependentCommand() {
        Instant createdAt = Instant.parse(
                "2026-08-01T00:00:00.123456789Z"
        );

        SubscriptionPurchaseCompletionCommand command =
                mapper.toPurchaseCompletionCommand(
                        event("shop_order", createdAt),
                        payload(false)
                );

        assertAll(
                () -> assertEquals(
                        "TRIBUTE",
                        command.provider()
                ),
                () -> assertEquals(
                        "shop_order:" + EXTERNAL_ORDER_ID,
                        command.externalEventId()
                ),
                () -> assertEquals(
                        EXTERNAL_ORDER_ID,
                        command.externalCheckoutId()
                ),
                () -> assertNull(command.externalCustomerId()),
                () -> assertEquals(
                        EXTERNAL_ORDER_ID,
                        command.externalOrderId()
                ),
                () -> assertNull(command.externalSubscriptionId()),
                () -> assertEquals(100_000L, command.paidAmountMinor()),
                () -> assertEquals("RUB", command.paidCurrency()),
                () -> assertEquals(
                        createdAt,
                        command.periodStart()
                ),
                () -> assertEquals(
                        Instant.parse(
                                "2026-09-01T00:00:00.123456789Z"
                        ),
                        command.periodEnd()
                )
        );
    }

    @Test
    void shouldUseCalendarMonthAtEndOfLeapYearFebruary() {
        SubscriptionPurchaseCompletionCommand command =
                mapper.toPurchaseCompletionCommand(
                        event(
                                "shop_order",
                                Instant.parse("2024-01-31T12:30:00Z")
                        ),
                        payload(false)
                );

        assertEquals(
                Instant.parse("2024-02-29T12:30:00Z"),
                command.periodEnd()
        );
    }

    @Test
    void shouldUseCalendarMonthAtEndOfOrdinaryFebruary() {
        SubscriptionPurchaseCompletionCommand command =
                mapper.toPurchaseCompletionCommand(
                        event(
                                "shop_order",
                                Instant.parse("2025-01-31T12:30:00Z")
                        ),
                        payload(false)
                );

        assertEquals(
                Instant.parse("2025-02-28T12:30:00Z"),
                command.periodEnd()
        );
    }

    @Test
    void shouldMoveDecemberPeriodIntoNextYear() {
        SubscriptionPurchaseCompletionCommand command =
                mapper.toPurchaseCompletionCommand(
                        event(
                                "shop_order",
                                Instant.parse("2026-12-15T20:45:00Z")
                        ),
                        payload(false)
                );

        assertEquals(
                Instant.parse("2027-01-15T20:45:00Z"),
                command.periodEnd()
        );
    }

    @Test
    void shouldCreateDeterministicEventIdentityForRetry() {
        TributeWebhookEvent event = event(
                "shop_order",
                Instant.parse("2026-08-01T00:00:00Z")
        );

        SubscriptionPurchaseCompletionCommand first =
                mapper.toPurchaseCompletionCommand(
                        event,
                        payload(false)
                );
        SubscriptionPurchaseCompletionCommand retry =
                mapper.toPurchaseCompletionCommand(
                        event,
                        payload(false)
                );

        assertEquals(
                first.externalEventId(),
                retry.externalEventId()
        );
    }

    @Test
    void shouldNotChangeIdentityForRecurrenceMarker() {
        TributeWebhookEvent event = event(
                "shop_order",
                Instant.parse("2026-08-01T00:00:00Z")
        );

        SubscriptionPurchaseCompletionCommand nonRecurrent =
                mapper.toPurchaseCompletionCommand(
                        event,
                        payload(false)
                );
        SubscriptionPurchaseCompletionCommand recurrent =
                mapper.toPurchaseCompletionCommand(
                        event,
                        payload(true)
                );

        assertEquals(
                nonRecurrent,
                recurrent
        );
    }

    @Test
    void shouldRejectUnsupportedEvent() {
        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> mapper.toPurchaseCompletionCommand(
                        event(
                                "shop_order_charge_success",
                                Instant.parse("2026-08-01T00:00:00Z")
                        ),
                        payload(false)
                )
        );

        assertAll(
                () -> assertEquals(
                        "Некорректные данные webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertNull(exception.getCause())
        );
    }

    @Test
    void shouldRejectMissingEvent() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> mapper.toPurchaseCompletionCommand(
                        null,
                        payload(false)
                )
        );

        assertEquals(
                "Событие Tribute не должно быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingPayload() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> mapper.toPurchaseCompletionCommand(
                        event(
                                "shop_order",
                                Instant.parse("2026-08-01T00:00:00Z")
                        ),
                        null
                )
        );

        assertEquals(
                "Payload заказа Tribute не должен быть null",
                exception.getMessage()
        );
    }

    private TributeWebhookEvent event(
            String name,
            Instant createdAt
    ) {
        return new TributeWebhookEvent(
                name,
                createdAt,
                sentAtAfter(createdAt),
                JsonMapper.builder().build().createObjectNode()
        );
    }

    private Instant sentAtAfter(Instant createdAt) {
        return createdAt.isBefore(SENT_AT)
                ? SENT_AT
                : createdAt.plusSeconds(1);
    }

    private TributeShopOrderPayload payload(
            boolean recurrent
    ) {
        return new TributeShopOrderPayload(
                ORDER_ID,
                100_000L,
                "rub",
                "paid",
                recurrent,
                "monthly"
        );
    }
}
