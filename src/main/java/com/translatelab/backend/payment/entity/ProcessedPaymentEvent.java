package com.translatelab.backend.payment.entity;

import com.google.errorprone.annotations.Immutable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_payment_events")
@Immutable
public class ProcessedPaymentEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    String provider;

    @Column(name = "external_event_id", nullable = false, updatable = false, length = 255)
    String externalEventId;

    @Column(name = "eventType", nullable = false, updatable = false, length = 128)
    String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false, insertable = false)
    Instant processedAt;

    protected ProcessedPaymentEvent(){}

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}