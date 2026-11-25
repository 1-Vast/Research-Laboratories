package com.labflow.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 刷新令牌。数据库只保存 SHA-256 哈希；一次性使用，重放视为凭证泄露并吊销整个会话。
 */
public class RefreshToken {

    private UUID id;
    private UUID sessionId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
    private Instant createdAt;

    private RefreshToken() {
    }

    public static RefreshToken issue(UUID sessionId, String tokenHash, Instant now, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.sessionId = sessionId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    public static RefreshToken restore(UUID id, UUID sessionId, String tokenHash, Instant expiresAt,
                                       Instant usedAt, Instant revokedAt, Instant createdAt) {
        RefreshToken token = new RefreshToken();
        token.id = id;
        token.sessionId = sessionId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.usedAt = usedAt;
        token.revokedAt = revokedAt;
        token.createdAt = createdAt;
        return token;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isReplayed() {
        return usedAt != null || revokedAt != null;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
