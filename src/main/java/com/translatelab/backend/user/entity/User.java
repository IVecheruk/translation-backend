package com.translatelab.backend.user.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    protected User() {}

    public User (
            String email,
            String passwordHash
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
        this.emailVerifiedAt = null;
        this.authVersion = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public void verifyEmail(Instant verifiedAt) {
        if (verifiedAt == null) {
            throw new IllegalArgumentException(
                    "Момент подтверждения email не должен быть null"
            );
        }
        if (!emailVerified) {
            emailVerified = true;
            emailVerifiedAt = verifiedAt;
        }
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Хеш пароля не должен быть пустым"
            );
        }
        passwordHash = newPasswordHash;
        revokeSessions();
    }

    public void revokeSessions() {
        authVersion = Math.addExact(authVersion, 1);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
