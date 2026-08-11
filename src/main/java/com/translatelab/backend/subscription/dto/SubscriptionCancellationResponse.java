package com.translatelab.backend.subscription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record SubscriptionCancellationResponse(
        @JsonProperty("cancel_at_period_end") boolean cancelAtPeriodEnd,
        @JsonProperty("effective_at") Instant effectiveAt
) {}
