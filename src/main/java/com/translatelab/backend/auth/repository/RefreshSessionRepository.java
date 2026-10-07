package com.translatelab.backend.auth.repository;

import com.translatelab.backend.auth.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Query("""
            SELECT COUNT(session) > 0 FROM RefreshSession session
            WHERE session.id = :sessionId AND session.user.id = :userId
                AND session.revokedAt IS NULL AND session.expiresAt > :now
                AND session.authVersion = session.user.authVersion
            """)
    boolean isActive(@Param("sessionId") UUID sessionId,
                     @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            DELETE FROM refresh_sessions WHERE id IN (
                SELECT id FROM refresh_sessions WHERE expires_at <= :now
                ORDER BY expires_at LIMIT 500
            )
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now);
}
