package com.translatelab.backend.messaging.outbox.entity;

public enum TranslationOutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    EXHAUSTED
}
