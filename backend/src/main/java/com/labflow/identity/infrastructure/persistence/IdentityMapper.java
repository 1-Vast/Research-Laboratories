package com.labflow.identity.infrastructure.persistence;

import com.labflow.identity.domain.AuthSession;
import com.labflow.identity.domain.Permission;
import com.labflow.identity.domain.RefreshToken;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 领域对象与 JPA 实体之间的映射，仅存在于基础设施层。 */
final class IdentityMapper {

    private IdentityMapper() {
    }

    static Permission toDomain(PermissionJpaEntity entity) {
        return new Permission(entity.getId(), entity.getCode(), entity.getDescription());
    }

    static Role toDomain(RoleJpaEntity entity) {
        Set<Permission> permissions = entity.getPermissions().stream()
                .map(IdentityMapper::toDomain)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Role.restore(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.isProtectedRole(), entity.getCreatedAt(), entity.getUpdatedAt(), permissions);
    }

    static User toDomain(UserJpaEntity entity) {
        Set<Role> roles = entity.getRoles().stream()
                .map(IdentityMapper::toDomain)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return User.restore(entity.getId(), entity.getUsername(), entity.getPasswordHash(), entity.getEmail(),
                entity.getDisplayName(), entity.isEnabled(), entity.getAuthVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt(), roles);
    }

    static void copyToEntity(User user, UserJpaEntity entity) {
        entity.setUsername(user.username());
        entity.setPasswordHash(user.passwordHash());
        entity.setEmail(user.email());
        entity.setDisplayName(user.displayName());
        entity.setEnabled(user.enabled());
        entity.setAuthVersion(user.authVersion());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
    }

    static void copyToEntity(Role role, RoleJpaEntity entity) {
        entity.setCode(role.code());
        entity.setName(role.name());
        entity.setDescription(role.description());
        entity.setProtectedRole(role.protectedRole());
        entity.setCreatedAt(role.createdAt());
        entity.setUpdatedAt(role.updatedAt());
    }

    static AuthSession toDomain(AuthSessionJpaEntity entity) {
        return AuthSession.restore(entity.getId(), entity.getUserId(), entity.getAuthVersion(), entity.getIssuedAt(),
                entity.getExpiresAt(), entity.getRevokedAt(), entity.getLastUsedAt(), entity.getClientIp(),
                entity.getUserAgent());
    }

    static void copyToEntity(AuthSession session, AuthSessionJpaEntity entity) {
        entity.setId(session.id());
        entity.setUserId(session.userId());
        entity.setAuthVersion(session.authVersion());
        entity.setIssuedAt(session.issuedAt());
        entity.setExpiresAt(session.expiresAt());
        entity.setRevokedAt(session.revokedAt());
        entity.setLastUsedAt(session.lastUsedAt());
        entity.setClientIp(session.clientIp());
        entity.setUserAgent(session.userAgent());
    }

    static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.restore(entity.getId(), entity.getSessionId(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getUsedAt(), entity.getRevokedAt(), entity.getCreatedAt());
    }

    static void copyToEntity(RefreshToken token, RefreshTokenJpaEntity entity) {
        entity.setId(token.id());
        entity.setSessionId(token.sessionId());
        entity.setTokenHash(token.tokenHash());
        entity.setExpiresAt(token.expiresAt());
        entity.setUsedAt(token.usedAt());
        entity.setRevokedAt(token.revokedAt());
        entity.setCreatedAt(token.createdAt());
    }
}
