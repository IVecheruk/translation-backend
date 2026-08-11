package com.translatelab.backend.subscription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AccountSubscriptionResponse(
        @JsonProperty("plan_code") String planCode,
        @JsonProperty("plan_display_name") String planDisplayName,
        String status,
        @JsonProperty("current_period_start") Instant currentPeriodStart,
        @JsonProperty("current_period_end") Instant currentPeriodEnd,
        @JsonProperty("cancel_at_period_end") boolean cancelAtPeriodEnd
) {}
