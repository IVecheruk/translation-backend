package com.translatelab.backend.auth.entity;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_action_tokens")
public class AccountActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private AccountActionTokenType type;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountActionToken() {}

    private AccountActionToken(
            User user,
            AccountActionTokenType type,
            String tokenHash,
            Instant now,
            Instant expiresAt
    ) {
        if (user == null || type == null || now == null || expiresAt == null) {
            throw new IllegalArgumentException(
                    "Параметры одноразового токена не должны быть null"
            );
        }
        if (tokenHash == null || !tokenHash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Некорректный хеш токена");
        }
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Срок токена должен быть позже момента создания"
            );
        }
        this.user = user;
        this.type = type;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static AccountActionToken active(
            User user,
            AccountActionTokenType type,
            String tokenHash,
            Instant now,
            Instant expiresAt
    ) {
        return new AccountActionToken(user, type, tokenHash, now, expiresAt);
    }

    public void consume(Instant now) {
        if (now == null || consumedAt != null || !now.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "Одноразовый токен недействителен"
            );
        }
        consumedAt = now;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public AccountActionTokenType getType() { return type; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
