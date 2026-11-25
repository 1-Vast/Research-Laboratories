package com.labflow.shared.security;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验。
 * <p>令牌只承担“我是谁、属于哪个会话”的身份声明，权限一律在每次请求时从数据库加载，
 * 因此管理员修改角色权限后无需重启，也不存在把权限写死在令牌里导致越权的问题。</p>
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final String issuer;
    private final java.time.Duration accessTtl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.issuer = properties.issuer();
        this.accessTtl = properties.accessTtl();
        this.clock = clock;
        this.signingKey = buildKey(properties.signingKey());
    }

    private SecretKey buildKey(String configured) {
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SIGNING_KEY 未配置，已生成本次启动专用的随机密钥；重启后旧令牌全部失效。"
                    + "生产环境必须通过环境变量注入固定密钥。");
            return Keys.hmacShaKeyFor(random);
        }
        byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SIGNING_KEY 长度不足 32 字节，无法满足 HS256 安全要求");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issueAccessToken(Long userId, UUID sessionId, int authVersion) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTtl);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("av", authVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            UUID sessionId = UUID.fromString(claims.get("sid", String.class));
            Integer authVersion = claims.get("av", Integer.class);
            return new AccessTokenClaims(userId, sessionId, authVersion == null ? -1 : authVersion,
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public record AccessTokenClaims(Long userId, UUID sessionId, int authVersion, Instant expiresAt) {
    }
}
