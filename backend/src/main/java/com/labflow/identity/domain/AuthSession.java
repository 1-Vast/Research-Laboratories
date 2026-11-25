package com.labflow.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 服务端会话。JWT 只是会话的载体：令牌里的 sid 必须能查到一条未吊销、未过期的会话，
 * 因此“退出登录”在服务端真实生效，旧令牌重放一律 401。
 */
public class AuthSession {

    private UUID id;
    private Long userId;
    private int authVersion;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant lastUsedAt;
    private String clientIp;
    private String userAgent;

    private AuthSession() {
    }

    public static AuthSession open(Long userId, int authVersion, Instant now, java.time.Duration ttl,
                                   String clientIp, String userAgent) {
        AuthSession session = new AuthSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.authVersion = authVersion;
        session.issuedAt = now;
        session.expiresAt = now.plus(ttl);
        session.clientIp = clientIp;
        session.userAgent = userAgent == null ? null : truncate(userAgent, 255);
        return session;
    }

    public static AuthSession restore(UUID id, Long userId, int authVersion, Instant issuedAt, Instant expiresAt,
                                      Instant revokedAt, Instant lastUsedAt, String clientIp, String userAgent) {
        AuthSession session = new AuthSession();
        session.id = id;
        session.userId = userId;
        session.authVersion = authVersion;
        session.issuedAt = issuedAt;
        session.expiresAt = expiresAt;
        session.revokedAt = revokedAt;
        session.lastUsedAt = lastUsedAt;
        session.clientIp = clientIp;
        session.userAgent = userAgent;
        return session;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public int authVersion() {
        return authVersion;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public String clientIp() {
        return clientIp;
    }

    public String userAgent() {
        return userAgent;
    }
}
