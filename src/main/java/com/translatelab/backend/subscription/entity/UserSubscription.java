package com.translatelab.backend.subscription.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "plan_code",
            nullable = false,
            updatable = false
    )
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "external_customer_id", length = 255)
    private String externalCustomerId;

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSubscription() {}

    private UserSubscription(
            User user,
            SubscriptionPlan plan,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            String provider,
            String externalCustomerId,
            String externalSubscriptionId
    ) {
        this.user = validateUser(user);
        this.plan = validatePlan(plan);
        this.currentPeriodStart =
                validatePeriodStart(currentPeriodStart);
        this.currentPeriodEnd = validatePeriodEnd(
                this.currentPeriodStart,
                currentPeriodEnd
        );
        this.provider = validateOptionalProvider(provider);
        this.externalCustomerId = normalizeOptionalExternalId(
                externalCustomerId,
                "Внешний идентификатор клиента"
        );
        this.externalSubscriptionId = normalizeOptionalExternalId(
                externalSubscriptionId,
                "Внешний идентификатор подписки"
        );

        validateProviderBinding(
                this.provider,
                this.externalCustomerId,
                this.externalSubscriptionId
        );

        this.status = SubscriptionStatus.ACTIVE;
        this.cancelAtPeriodEnd = false;
    }

    public static UserSubscription manual(
            User user,
            SubscriptionPlan plan,
            Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {
        return new UserSubscription(
                user,
                plan,
                currentPeriodStart,
                currentPeriodEnd,
                null,
                null,
                null
        );
    }

    public static UserSubscription providerManaged(
            User user,
            SubscriptionPlan plan,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            String provider,
            String externalCustomerId,
            String externalSubscriptionId
    ) {
        return new UserSubscription(
                user,
                plan,
                currentPeriodStart,
                currentPeriodEnd,
                provider,
                externalCustomerId,
                externalSubscriptionId
        );
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalCustomerId() {
        return externalCustomerId;
    }

    public String getExternalSubscriptionId() {
        return externalSubscriptionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static User validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Пользователь не должен быть null"
            );
        }

        return user;
    }

    private static SubscriptionPlan validatePlan(
            SubscriptionPlan plan
    ) {
        if (plan == null) {
            throw new IllegalArgumentException(
                    "Тариф не должен быть null"
            );
        }

        if (!plan.isActive()) {
            throw new IllegalArgumentException(
                    "Нельзя создать подписку на неактивный тариф"
            );
        }

        return plan;
    }

    private static Instant validatePeriodStart(
            Instant currentPeriodStart
    ) {
        if (currentPeriodStart == null) {
            throw new IllegalArgumentException(
                    "Начало периода подписки не должно быть null"
            );
        }

        return currentPeriodStart;
    }

    private static Instant validatePeriodEnd(
            Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {
        if (currentPeriodEnd == null) {
            throw new IllegalArgumentException(
                    "Конец периода подписки не должен быть null"
            );
        }

        if (!currentPeriodEnd.isAfter(currentPeriodStart)) {
            throw new IllegalArgumentException(
                    "Конец периода подписки должен быть позже его начала"
            );
        }

        return currentPeriodEnd;
    }

    private static String validateOptionalProvider(String provider) {
        if (provider == null) {
            return null;
        }

        if (!PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного провайдера"
            );
        }

        return provider;
    }

    private static String normalizeOptionalExternalId(
            String externalId,
            String fieldName
    ) {
        if (externalId == null) {
            return null;
        }

        String normalized = externalId.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " не должен быть пустым"
            );
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    fieldName + " не должен превышать 255 символов"
            );
        }

        return normalized;
    }

    private static void validateProviderBinding(
            String provider,
            String externalCustomerId,
            String externalSubscriptionId
    ) {
        if (provider == null) {
            if (externalCustomerId != null
                    || externalSubscriptionId != null) {
                throw new IllegalArgumentException(
                        "Внешние идентификаторы требуют платёжного провайдера"
                );
            }

            return;
        }

        if (externalSubscriptionId == null) {
            throw new IllegalArgumentException(
                    "Для платёжного провайдера требуется "
                            + "внешний идентификатор подписки"
            );
        }
    }
}