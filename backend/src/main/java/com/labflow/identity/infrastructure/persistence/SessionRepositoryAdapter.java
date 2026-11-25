package com.labflow.identity.infrastructure.persistence;

import com.labflow.identity.domain.AuthSession;
import com.labflow.identity.domain.repository.SessionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepositoryAdapter implements SessionRepository {

    private final AuthSessionJpaRepository sessions;

    public SessionRepositoryAdapter(AuthSessionJpaRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public AuthSession save(AuthSession session) {
        AuthSessionJpaEntity entity = sessions.findById(session.id()).orElseGet(AuthSessionJpaEntity::new);
        IdentityMapper.copyToEntity(session, entity);
        return IdentityMapper.toDomain(sessions.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthSession> findById(UUID id) {
        return sessions.findById(id).map(IdentityMapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllByUserId(Long userId, Instant now) {
        sessions.revokeAllByUserId(userId, now);
    }
}
