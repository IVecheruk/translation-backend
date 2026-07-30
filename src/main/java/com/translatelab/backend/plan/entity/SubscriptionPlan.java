package com.translatelab.backend.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.regex.Pattern;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionPlan() {}

    public SubscriptionPlan(String code, String displayName){
        this.code = validateCode(code);
        this.displayName = normalizeDisplayName(displayName);
        this.active = true;
    }

    public void rename(String displayName){
        this.displayName = normalizeDisplayName(displayName);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
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
        if (code == null) {
            throw new IllegalArgumentException("Код тарифа не должен быть null");
        }

        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Некорректный формат кода тарифа");
        }

        return code;
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Название тарифа не должно быть пустым");
        }

        String normalized = displayName.strip();

        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Название тарифа не должно превышать 100 символов");
        }

        return normalized;
    }
}