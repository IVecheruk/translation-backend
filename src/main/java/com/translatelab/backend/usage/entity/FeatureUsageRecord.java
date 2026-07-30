package com.translatelab.backend.usage.entity;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_usage_records")
public class FeatureUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "feature_code",
            nullable = false,
            updatable = false,
            length = 64
    )
    private FeatureCode featureCode;

    @Column(name = "units", nullable = false, updatable = false)
    private int units;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UsageStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translation_job_id")
    private TranslationJob translationJob;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureUsageRecord() {}

    private FeatureUsageRecord(
            User user,
            FeatureCode featureCode,
            int units,
            Instant periodStart,
            Instant periodEnd,
            Instant expiresAt
    ) {
        this.user = validateUser(user);
        this.featureCode = validateFeatureCode(featureCode);
        this.units = validateUnits(units);
        this.periodStart = validatePeriodStart(periodStart);
        this.periodEnd = validatePeriodEnd(periodStart, periodEnd);
        this.expiresAt = validateExpiresAt(expiresAt);
        this.status = UsageStatus.RESERVED;
        this.translationJob = null;
    }

    public static FeatureUsageRecord reserve(
            User user,
            FeatureCode featureCode,
            int units,
            Instant periodStart,
            Instant periodEnd,
            Instant expiresAt
    ) {
        return new FeatureUsageRecord(
                user,
                featureCode,
                units,
                periodStart,
                periodEnd,
                expiresAt
        );
    }

    public void consume(TranslationJob translationJob) {
        ensureReserved();

        if (translationJob == null) {
            throw new IllegalArgumentException(
                    "Задание перевода не должно быть null"
            );
        }

        ensureSameUser(translationJob);

        this.translationJob = translationJob;
        this.status = UsageStatus.CONSUMED;
        this.expiresAt = null;
    }

    public void release() {
        ensureReserved();

        this.status = UsageStatus.RELEASED;
        this.expiresAt = null;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public FeatureCode getFeatureCode() {
        return featureCode;
    }

    public int getUnits() {
        return units;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public UsageStatus getStatus() {
        return status;
    }

    public TranslationJob getTranslationJob() {
        return translationJob;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void ensureReserved() {
        if (this.status != UsageStatus.RESERVED) {
            throw new IllegalStateException(
                    "Изменить можно только активную резервацию"
            );
        }
    }

    private void ensureSameUser(TranslationJob translationJob) {
        User jobUser = translationJob.getUser();

        if (jobUser == null) {
            throw new IllegalArgumentException(
                    "Задание перевода должно принадлежать пользователю"
            );
        }

        UUID usageUserId = this.user.getId();
        UUID jobUserId = jobUser.getId();

        if (usageUserId != null && jobUserId != null) {
            if (!usageUserId.equals(jobUserId)) {
                throw new IllegalArgumentException(
                        "Задание перевода принадлежит другому пользователю"
                );
            }

            return;
        }

        if (this.user != jobUser) {
            throw new IllegalArgumentException(
                    "Задание перевода принадлежит другому пользователю"
            );
        }
    }

    private static User validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Пользователь не должен быть null"
            );
        }

        return user;
    }

    private static FeatureCode validateFeatureCode(
            FeatureCode featureCode
    ) {
        if (featureCode == null) {
            throw new IllegalArgumentException(
                    "Код функции не должен быть null"
            );
        }

        return featureCode;
    }

    private static int validateUnits(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "Количество единиц должно быть положительным"
            );
        }

        return units;
    }

    private static Instant validatePeriodStart(Instant periodStart) {
        if (periodStart == null) {
            throw new IllegalArgumentException(
                    "Начало периода не должно быть null"
            );
        }

        return periodStart;
    }

    private static Instant validatePeriodEnd(
            Instant periodStart,
            Instant periodEnd
    ) {
        if (periodEnd == null) {
            throw new IllegalArgumentException(
                    "Конец периода не должен быть null"
            );
        }

        if (periodStart != null && !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException(
                    "Конец периода должен быть позже его начала"
            );
        }

        return periodEnd;
    }

    private static Instant validateExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Срок действия резервации не должен быть null"
            );
        }

        return expiresAt;
    }
}