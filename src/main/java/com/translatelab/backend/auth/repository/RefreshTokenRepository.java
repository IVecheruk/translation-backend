package com.translatelab.backend.auth.repository;

import com.translatelab.backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    // Resolve only the scalar owner ID before taking the account lock. Loading
    // an entity here would leave a stale consumed_at value during concurrent refresh.
    @Query("SELECT token.session.user.id FROM RefreshToken token WHERE token.tokenHash = :hash")
    Optional<UUID> findOwnerId(@Param("hash") String hash);
}
