package com.translatelab.backend.user.repository;

import com.translatelab.backend.user.entity.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select profile
            from UserProfile profile
            where profile.userId = :userId
            """)
    Optional<UserProfile> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsByAvatarObjectKey(String avatarObjectKey);
}
