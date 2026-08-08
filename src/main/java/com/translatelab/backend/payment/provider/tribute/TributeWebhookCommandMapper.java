package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeWebhookCommandMapper {

    private static final String PROVIDER_CODE =
            "TRIBUTE";

    private static final String SHOP_ORDER_EVENT =
            "shop_order";

    private static final String EVENT_ID_PREFIX =
            "shop_order:";

    public SubscriptionPurchaseCompletionCommand
    toPurchaseCompletionCommand(
            TributeWebhookEvent event,
            TributeShopOrderPayload payload
    ) {
        Objects.requireNonNull(
                event,
                "Событие Tribute не должно быть null"
        );

        Objects.requireNonNull(
                payload,
                "Payload заказа Tribute не должен быть null"
        );

        if (!SHOP_ORDER_EVENT.equals(event.name())) {
            throw new InvalidTributeWebhookException();
        }

        Instant periodStart = event.createdAt();

        Instant periodEnd = periodStart
                .atZone(ZoneOffset.UTC)
                .plusMonths(1)
                .toInstant();

        String externalOrderId =
                payload.uuid().toString();

        return new SubscriptionPurchaseCompletionCommand(
                PROVIDER_CODE,
                EVENT_ID_PREFIX + externalOrderId,
                externalOrderId,
                externalOrderId,
                null,
                null,
                payload.amount(),
                payload.currency().toUpperCase(java.util.Locale.ROOT),
                BillingPeriod.MONTH,
                null,
                periodStart,
                periodEnd
        );
    }
}
