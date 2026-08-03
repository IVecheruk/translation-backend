package com.translatelab.backend.payment.entity;

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
@Table(name = "subscription_purchase_intents")
public class SubscriptionPurchaseIntent {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_code", nullable = true, updatable = false)
    private PlanPaymentOffer offer;

    @Column(
            name = "provider",
            nullable = false,
            updatable = false,
            length = 32
    )
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionPurchaseIntentStatus status;

    @Column(name = "external_checkout_id", length = 255)
    private String externalCheckoutId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

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

    protected SubscriptionPurchaseIntent() {}

    private SubscriptionPurchaseIntent(
            User user,
            PlanPaymentOffer offer,
            Instant now,
            Instant expiresAt
    ) {
        Instant validatedNow = validateNow(now);

        this.user = validateUser(user);
        this.offer = validateOffer(offer);
        this.plan = validatePlan(this.offer.getPlan());
        this.provider = validateProvider(this.offer.getProvider());
        this.expiresAt = validateExpiresAt(
                validatedNow,
                expiresAt
        );
        this.status = SubscriptionPurchaseIntentStatus.PENDING;
        this.externalCheckoutId = null;
        this.consumedAt = null;
    }


    public static SubscriptionPurchaseIntent pending(
            User user,
            PlanPaymentOffer offer,
            Instant now,
            Instant expiresAt
    ) {
        return new SubscriptionPurchaseIntent(
                user,
                offer,
                now,
                expiresAt
        );
    }

    public void attachCheckout(
            String externalCheckoutId,
            Instant now
    ) {
        ensureUsableAt(now);

        String normalizedCheckoutId = normalizeExternalCheckoutId(
                externalCheckoutId
        );

        if (this.externalCheckoutId != null) {
            if (!this.externalCheckoutId.equals(normalizedCheckoutId)) {
                throw new IllegalStateException(
                        "К заявке уже привязан другой checkout"
                );
            }

            return;
        }

        this.externalCheckoutId = normalizedCheckoutId;
    }

    public void consume(Instant now) {
        Instant validatedNow = ensureUsableAt(now);

        if (this.externalCheckoutId == null) {
            throw new IllegalStateException(
                    "Нельзя завершить заявку без привязанного checkout"
            );
        }

        this.status = SubscriptionPurchaseIntentStatus.CONSUMED;
        this.consumedAt = validatedNow;
    }

    public void expire(Instant now) {
        ensurePending();

        Instant validatedNow = validateNow(now);

        if (validatedNow.isBefore(this.expiresAt)) {
            throw new IllegalStateException(
                    "Срок действия заявки ещё не завершён"
            );
        }

        this.status = SubscriptionPurchaseIntentStatus.EXPIRED;
    }

    public void cancel() {
        ensurePending();
        this.status = SubscriptionPurchaseIntentStatus.CANCELED;
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

    public PlanPaymentOffer getOffer() {
        return offer;
    }

    public String getProvider() {
        return provider;
    }

    public SubscriptionPurchaseIntentStatus getStatus() {
        return status;
    }

    public String getExternalCheckoutId() {
        return externalCheckoutId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private Instant ensureUsableAt(Instant now) {
        ensurePending();

        Instant validatedNow = validateNow(now);

        if (!validatedNow.isBefore(this.expiresAt)) {
            throw new IllegalStateException(
                    "Срок действия заявки завершён"
            );
        }

        return validatedNow;
    }

    private void ensurePending() {
        if (this.status != SubscriptionPurchaseIntentStatus.PENDING) {
            throw new IllegalStateException(
                    "Изменить можно только ожидающую заявку"
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

    private static PlanPaymentOffer validateOffer(
            PlanPaymentOffer offer
    ) {
        if (offer == null) {
            throw new IllegalArgumentException(
                    "Платёжное предложение не должно быть null"
            );
        }

        if (!offer.isActive()) {
            throw new IllegalArgumentException(
                    "Нельзя создать заявку для неактивного платёжного предложения"
            );
        }

        return offer;
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
                    "Нельзя создать заявку для неактивного тарифа"
            );
        }

        return plan;
    }

    private static String validateProvider(String provider) {
        if (provider == null
                || !PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного провайдера"
            );
        }

        return provider;
    }

    private static Instant validateNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException(
                    "Текущий момент не должен быть null"
            );
        }

        return now;
    }

    private static Instant validateExpiresAt(
            Instant now,
            Instant expiresAt
    ) {
        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Срок действия заявки не должен быть null"
            );
        }

        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Срок действия заявки должен быть позже текущего момента"
            );
        }

        return expiresAt;
    }

    private static String normalizeExternalCheckoutId(
            String externalCheckoutId
    ) {
        if (externalCheckoutId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор checkout не должен быть null"
            );
        }

        String normalized = externalCheckoutId.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Идентификатор checkout не должен быть пустым"
            );
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Идентификатор checkout не должен превышать 255 символов"
            );
        }

        return normalized;
    }
}