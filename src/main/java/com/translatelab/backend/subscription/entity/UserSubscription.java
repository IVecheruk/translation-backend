package com.translatelab.backend.subscription.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.payment.entity.BillingPeriod;
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

    @Column(name = "external_order_id", length = 255)
    private String externalOrderId;

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;

    @Column(name = "price_minor")
    private Long priceMinor;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", length = 16)
    private BillingPeriod billingPeriod;

    @Column(name = "external_product_id", length = 255)
    private String externalProductId;

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
            String externalOrderId,
            String externalSubscriptionId,
            Long priceMinor,
            String currency,
            BillingPeriod billingPeriod,
            String externalProductId
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
        this.externalOrderId = normalizeOptionalExternalId(
                externalOrderId,
                "Внешний идентификатор заказа"
        );
        this.externalSubscriptionId = normalizeOptionalExternalId(
                externalSubscriptionId,
                "Внешний идентификатор подписки"
        );

        validateProviderBinding(
                this.provider,
                this.externalCustomerId,
                this.externalOrderId,
                this.externalSubscriptionId
        );
        validateCommercialSnapshot(priceMinor, currency, billingPeriod);
        this.priceMinor = priceMinor;
        this.currency = currency;
        this.billingPeriod = billingPeriod;
        this.externalProductId = normalizeOptionalExternalId(
                externalProductId,
                "Внешний идентификатор продукта"
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
                null,
                null,
                null,
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
                null,
                externalSubscriptionId,
                null,
                null,
                null,
                null
        );
    }

    public static UserSubscription providerManagedPurchase(
            User user,
            SubscriptionPlan plan,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            String provider,
            String externalCustomerId,
            String externalOrderId,
            String externalSubscriptionId,
            long priceMinor,
            String currency,
            BillingPeriod billingPeriod,
            String externalProductId
    ) {
        return new UserSubscription(
                user,
                plan,
                currentPeriodStart,
                currentPeriodEnd,
                provider,
                externalCustomerId,
                externalOrderId,
                externalSubscriptionId,
                priceMinor,
                currency,
                billingPeriod,
                externalProductId
        );
    }

    public void scheduleCancellationAtPeriodEnd() {
        ensureActive();
        this.cancelAtPeriodEnd = true;
    }

    public void revokeCancellationAtPeriodEnd() {
        ensureActive();
        this.cancelAtPeriodEnd = false;
    }

    public void cancelImmediately() {
        ensureNonTerminal();

        this.status = SubscriptionStatus.CANCELED;
        this.cancelAtPeriodEnd = false;
    }

    public void expire(Instant now) {
        ensureNonTerminal();

        if (now == null) {
            throw new IllegalArgumentException(
                    "Момент окончания подписки не должен быть null"
            );
        }

        if (now.isBefore(this.currentPeriodEnd)) {
            throw new IllegalStateException(
                    "Подписка не может истечь до окончания " +
                            "оплаченного периода"
            );
        }

        this.status = SubscriptionStatus.EXPIRED;
        this.cancelAtPeriodEnd = false;
    }

    public void renewPaidPeriod(
            Instant newPeriodStart,
            Instant newPeriodEnd
    ) {
        ensureActive();

        if (this.cancelAtPeriodEnd) {
            throw new IllegalStateException(
                    "Нельзя продлить подписку " +
                            "с запланированной отменой"
            );
        }

        Instant validatedPeriodStart = validatePeriodStart(newPeriodStart);
        Instant validatedPeriodEnd = validatePeriodEnd(validatedPeriodStart, newPeriodEnd);

        if (!validatedPeriodStart.equals(this.currentPeriodEnd)) {
            throw new IllegalStateException(
                    "Новый период должен начинаться " +
                            "в момент окончания текущего периода"
            );
        }

        this.currentPeriodStart = validatedPeriodStart;
        this.currentPeriodEnd = validatedPeriodEnd;
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

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public String getExternalSubscriptionId() {
        return externalSubscriptionId;
    }

    public Long getPriceMinor() {
        return priceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public String getExternalProductId() {
        return externalProductId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markPastDue() {
        ensureActive();

        this.status = SubscriptionStatus.PAST_DUE;
        this.cancelAtPeriodEnd = false;
    }

    public void recoverAfterPayment(
            Instant newPeriodStart,
            Instant newPeriodEnd
    ) {
        ensurePastDue();

        Instant validatedPeriodStart = validatePeriodStart(newPeriodStart);
        Instant validatedPeriodEnd = validatePeriodEnd(validatedPeriodStart, newPeriodEnd);

        this.currentPeriodStart = validatedPeriodStart;
        this.currentPeriodEnd = validatedPeriodEnd;
        this.status = SubscriptionStatus.ACTIVE;
        this.cancelAtPeriodEnd = false;
    }

    public void applySuccessfulRecurringCharge(
            Instant newPeriodStart,
            Instant newPeriodEnd
    ) {
        if (status == SubscriptionStatus.ACTIVE) {
            renewPaidPeriod(newPeriodStart, newPeriodEnd);
            return;
        }
        if (status == SubscriptionStatus.PAST_DUE) {
            recoverAfterPayment(newPeriodStart, newPeriodEnd);
            return;
        }
        throw new IllegalStateException(
                "Успешное списание недоступно для завершённой подписки"
        );
    }

    public void applyProviderCancellation() {
        if (status == SubscriptionStatus.ACTIVE) {
            cancelAtPeriodEnd = true;
            return;
        }
        if (status == SubscriptionStatus.PAST_DUE) {
            status = SubscriptionStatus.CANCELED;
            cancelAtPeriodEnd = false;
        }
    }

    public void revokeByProvider() {
        if (status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.PAST_DUE) {
            status = SubscriptionStatus.CANCELED;
            cancelAtPeriodEnd = false;
        }
    }

    public boolean matchesCommercialSnapshot(
            long amountMinor,
            String paidCurrency,
            BillingPeriod paidBillingPeriod
    ) {
        return priceMinor != null
                && priceMinor == amountMinor
                && currency != null
                && currency.equals(paidCurrency)
                && billingPeriod == paidBillingPeriod;
    }

    private void ensureActive() {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Операция доступна только для активной подписки"
            );
        }
    }

    private void ensurePastDue() {
        if (this.status != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException(
                    "Операция доступна только для подписки с просроченной оплатой"
            );
        }
    }

    private void ensureNonTerminal() {
        if (this.status != SubscriptionStatus.ACTIVE
                && this.status != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException(
                    "Операция недоступна для завершённой подписки"
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
            String externalOrderId,
            String externalSubscriptionId
    ) {
        if (provider == null) {
            if (externalCustomerId != null
                    || externalOrderId != null
                    || externalSubscriptionId != null) {
                throw new IllegalArgumentException(
                        "Внешние идентификаторы требуют платёжного провайдера"
                );
            }

            return;
        }

        if (externalOrderId == null && externalSubscriptionId == null) {
            throw new IllegalArgumentException(
                    "Для платёжного провайдера требуется внешний "
                            + "идентификатор заказа или подписки"
            );
        }
    }

    private static void validateCommercialSnapshot(
            Long priceMinor,
            String currency,
            BillingPeriod billingPeriod
    ) {
        boolean allMissing = priceMinor == null
                && currency == null
                && billingPeriod == null;
        boolean allPresent = priceMinor != null
                && priceMinor > 0
                && currency != null
                && currency.matches("^[A-Z]{3}$")
                && billingPeriod != null;

        if (!allMissing && !allPresent) {
            throw new IllegalArgumentException(
                    "Коммерческий снимок подписки заполнен некорректно"
            );
        }
    }
}
