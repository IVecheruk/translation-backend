package com.translatelab.backend.auth.entity;

import com.translatelab.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "auth_version", nullable = false, updatable = false)
    private long authVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshSession() {}

    public RefreshSession(User user, Instant now, Instant expiresAt) {
        if (user == null || now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Invalid refresh session parameters");
        }
        this.user = user;
        this.authVersion = user.getAuthVersion();
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(User owner, Instant now) {
        return revokedAt == null && now.isBefore(expiresAt)
                && user.getId().equals(owner.getId())
                && authVersion == owner.getAuthVersion();
    }

    public void revoke(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Revocation time is required");
        }
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public long getAuthVersion() { return authVersion; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
