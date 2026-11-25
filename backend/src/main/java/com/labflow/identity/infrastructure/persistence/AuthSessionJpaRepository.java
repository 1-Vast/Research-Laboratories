package com.labflow.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionJpaEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSessionJpaEntity s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
