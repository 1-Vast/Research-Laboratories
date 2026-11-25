package com.labflow.identity.domain.repository;

import com.labflow.identity.domain.AuthSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findById(UUID id);

    /** 用户被禁用或改密时吊销其全部会话。 */
    void revokeAllByUserId(Long userId, Instant now);
}
