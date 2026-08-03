package com.translatelab.backend.payment.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.regex.Pattern;

@Entity
@Table(name = "plan_payment_offers")
public class PlanPaymentOffer {

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,63}$"
    );

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "^[A-Z]{3}$"
    );

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_code", nullable = false, updatable = false)
    private SubscriptionPlan plan;

    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "price_minor", nullable = false, updatable = false)
    private long priceMinor;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, updatable = false, length = 16)
    private BillingPeriod billingPeriod;

    @Column(name = "external_product_id", updatable = false, length = 255)
    private String externalProductId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanPaymentOffer() {}

    public PlanPaymentOffer(
            String code,
            SubscriptionPlan plan,
            String provider,
            long priceMinor,
            String currency,
            BillingPeriod billingPeriod,
            String externalProductId
    ) {
        this.code = validateCode(code);
        this.plan = validatePlan(plan);
        this.provider = validateProvider(provider);
        this.priceMinor = validatePriceMinor(priceMinor);
        this.currency = validateCurrency(currency);
        this.billingPeriod = validateBillingPeriod(billingPeriod);
        this.externalProductId = normalizeExternalProductId(externalProductId);
        this.active = true;
    }

    public void activate() {
        if (!plan.isActive()) {
            throw new IllegalStateException(
                    "Нельзя активировать предложение неактивного тарифа"
            );
        }

        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getCode() {
        return code;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public String getProvider() {
        return provider;
    }

    public long getPriceMinor() {
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

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного предложения"
            );
        }

        return code;
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
                    "Нельзя создать предложение для неактивного тарифа"
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

    private static long validatePriceMinor(long priceMinor) {
        if (priceMinor <= 0) {
            throw new IllegalArgumentException(
                    "Цена должна быть больше нуля"
            );
        }

        return priceMinor;
    }

    private static String validateCurrency(String currency) {
        if (currency == null
                || !CURRENCY_PATTERN.matcher(currency).matches()) {
            throw new IllegalArgumentException(
                    "Валюта должна состоять из трёх заглавных латинских букв"
            );
        }

        return currency;
    }

    private static BillingPeriod validateBillingPeriod(
            BillingPeriod billingPeriod
    ) {
        if (billingPeriod == null) {
            throw new IllegalArgumentException(
                    "Период оплаты не должен быть null"
            );
        }

        return billingPeriod;
    }

    private static String normalizeExternalProductId(
            String externalProductId
    ) {
        if (externalProductId == null) {
            return null;
        }

        String normalized = externalProductId.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор продукта не должен быть пустым"
            );
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор продукта не должен превышать 255 символов"
            );
        }

        return normalized;
    }
}