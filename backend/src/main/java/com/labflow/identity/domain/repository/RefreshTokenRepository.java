package com.labflow.identity.domain.repository;

import com.labflow.identity.domain.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeBySessionId(UUID sessionId, Instant now);

    void revokeByUserId(Long userId, Instant now);
}
