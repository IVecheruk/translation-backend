package com.translatelab.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private RefreshSession session;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, RefreshSession session, Instant now) {
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")
                || session == null || now == null) {
            throw new IllegalArgumentException("Invalid refresh token parameters");
        }
        this.tokenHash = tokenHash;
        this.session = session;
        this.createdAt = now;
    }

    public void consume(Instant now) {
        if (now == null || consumedAt != null) {
            throw new IllegalStateException("Refresh token has already been consumed");
        }
        consumedAt = now;
    }

    public RefreshSession getSession() { return session; }
    public Instant getConsumedAt() { return consumedAt; }
}
