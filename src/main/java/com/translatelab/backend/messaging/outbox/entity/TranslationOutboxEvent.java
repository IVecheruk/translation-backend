package com.translatelab.backend.messaging.outbox.entity;

import com.translatelab.backend.translation.entity.TranslationJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "translation_outbox_events")
public class TranslationOutboxEvent {

    public static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private TranslationJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TranslationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TranslationOutboxEvent() {
    }

    public TranslationOutboxEvent(TranslationJob job, Instant availableAt) {
        this.job = Objects.requireNonNull(
                job,
                "Задание outbox-события не должно быть null"
        );
        this.availableAt = Objects.requireNonNull(
                availableAt,
                "Время доступности outbox-события не должно быть null"
        );
        this.status = TranslationOutboxStatus.PENDING;
        this.attemptCount = 0;
    }

    public void claim(Instant lockedUntil) {
        if (status != TranslationOutboxStatus.PENDING
                && status != TranslationOutboxStatus.PUBLISHING) {
            throw new IllegalStateException(
                    "Получить в обработку можно только ожидающее событие"
            );
        }
        this.lockedUntil = Objects.requireNonNull(
                lockedUntil,
                "Срок аренды outbox-события не должен быть null"
        );
        this.status = TranslationOutboxStatus.PUBLISHING;
        this.attemptCount++;
        this.lastError = null;
    }

    public void markPublished(Instant publishedAt) {
        requirePublishing();
        this.status = TranslationOutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(
                publishedAt,
                "Время публикации не должно быть null"
        );
        this.lockedUntil = null;
        this.lastError = null;
    }

    public void scheduleRetry(Instant availableAt, String error) {
        requirePublishing();
        this.status = TranslationOutboxStatus.PENDING;
        this.availableAt = Objects.requireNonNull(availableAt);
        this.lockedUntil = null;
        this.lastError = normalizeError(error);
    }

    public void markExhausted(String error) {
        requirePublishing();
        this.status = TranslationOutboxStatus.EXHAUSTED;
        this.lockedUntil = null;
        this.lastError = normalizeError(error);
    }

    private void requirePublishing() {
        if (status != TranslationOutboxStatus.PUBLISHING) {
            throw new IllegalStateException(
                    "Завершить можно только публикуемое outbox-событие"
            );
        }
    }

    private String normalizeError(String error) {
        String normalized = error == null || error.isBlank()
                ? "Unknown publishing failure"
                : error.strip();
        return normalized.substring(
                0,
                Math.min(normalized.length(), MAX_ERROR_LENGTH)
        );
    }

    public UUID getId() {
        return id;
    }

    public TranslationJob getJob() {
        return job;
    }

    public TranslationOutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
