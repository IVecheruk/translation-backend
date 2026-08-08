package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeRecurringOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeFailedOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeWebhookPayloadDecoder {

    private static final String SHOP_ORDER_EVENT =
            "shop_order";

    private final ObjectMapper objectMapper;

    public TributeWebhookPayloadDecoder(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "ObjectMapper не должен быть null"
        );
    }

    public TributeShopOrderPayload decodeShopOrder(
            TributeWebhookEvent event
    ) {
        Objects.requireNonNull(
                event,
                "Событие Tribute не должно быть null"
        );

        if (!SHOP_ORDER_EVENT.equals(event.name())) {
            throw new InvalidTributeWebhookException();
        }

        try {
            return objectMapper.treeToValue(
                    event.payload(),
                    TributeShopOrderPayload.class
            );
        } catch (
                JacksonException
                | IllegalArgumentException exception
        ) {
            throw new InvalidTributeWebhookException(
                    exception
            );
        }
    }

    public TributeRecurringOrderPayload decodeRecurringOrder(
            TributeWebhookEvent event
    ) {
        return decode(event, TributeRecurringOrderPayload.class);
    }

    public TributeFailedOrderPayload decodeFailedOrder(
            TributeWebhookEvent event
    ) {
        return decode(event, TributeFailedOrderPayload.class);
    }

    private <T> T decode(TributeWebhookEvent event, Class<T> type) {
        Objects.requireNonNull(event, "Событие Tribute не должно быть null");
        try {
            return objectMapper.treeToValue(event.payload(), type);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new InvalidTributeWebhookException(exception);
        }
    }
}
