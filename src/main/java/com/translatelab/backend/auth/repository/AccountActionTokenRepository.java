package com.translatelab.backend.auth.repository;

import com.translatelab.backend.auth.entity.AccountActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import com.translatelab.backend.auth.entity.AccountActionTokenType;
import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface AccountActionTokenRepository
        extends JpaRepository<AccountActionToken, UUID> {

    @Query("""
            SELECT token
            FROM AccountActionToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
              AND token.consumedAt IS NULL
            """)
    Optional<AccountActionToken> findOpenByHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM AccountActionToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
              AND token.consumedAt IS NULL
            """)
    Optional<AccountActionToken> findOpenByHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            UPDATE AccountActionToken token
            SET token.consumedAt = :now
            WHERE token.user.id = :userId
              AND token.type = :type
              AND token.consumedAt IS NULL
            """)
    int consumeOpenForUserAndType(
            @Param("userId") UUID userId,
            @Param("type") AccountActionTokenType type,
            @Param("now") Instant now
    );
}
