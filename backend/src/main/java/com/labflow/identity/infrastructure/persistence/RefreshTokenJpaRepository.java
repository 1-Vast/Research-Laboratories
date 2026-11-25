package com.labflow.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenJpaEntity> findBySessionId(UUID sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.sessionId = :sessionId and t.revokedAt is null")
    int revokeBySessionId(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenJpaEntity t set t.revokedAt = :now
            where t.revokedAt is null
              and t.sessionId in (select s.id from AuthSessionJpaEntity s where s.userId = :userId)
            """)
    int revokeByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
