package com.translatelab.backend.user.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "username", length = 30)
    private String username;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "profession", length = 100)
    private String profession;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "avatar_object_key", length = 1024)
    private String avatarObjectKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {}

    public UserProfile(User user) {
        this.user = Objects.requireNonNull(
                user,
                "Пользователь не должен быть null"
        );
    }

    public void updateDetails(
            String username,
            String displayName,
            String nickname,
            String profession,
            String bio
    ) {
        this.username = username;
        this.displayName = displayName;
        this.nickname = nickname;
        this.profession = profession;
        this.bio = bio;
    }

    public void replaceAvatar(String avatarObjectKey) {
        this.avatarObjectKey = avatarObjectKey;
    }

    public void removeAvatar() {
        this.avatarObjectKey = null;
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfession() {
        return profession;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}