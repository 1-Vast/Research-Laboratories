package com.labflow.identity.infrastructure.persistence;

import com.labflow.identity.domain.RefreshToken;
import com.labflow.identity.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository tokens;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity = tokens.findById(token.id()).orElseGet(RefreshTokenJpaEntity::new);
        IdentityMapper.copyToEntity(token, entity);
        return IdentityMapper.toDomain(tokens.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(IdentityMapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeBySessionId(UUID sessionId, Instant now) {
        tokens.revokeBySessionId(sessionId, now);
    }

    @Override
    @Transactional
    public void revokeByUserId(Long userId, Instant now) {
        tokens.revokeByUserId(userId, now);
    }
}
