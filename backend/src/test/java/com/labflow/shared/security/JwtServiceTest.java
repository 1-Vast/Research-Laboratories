package com.labflow.shared.security;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT 服务")
class JwtServiceTest {

    private static final Instant NOW = Instant.now();
    private static final String SECRET = "unit-test-signing-key-that-is-long-enough-0123456789";

    private JwtService service(Duration ttl, Instant now) {
        JwtProperties properties = new JwtProperties("labflow", SECRET, ttl, Duration.ofDays(7));
        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("签发与解析：主体、会话、授权版本一致")
    void issueAndParseRoundTrip() {
        Instant now = NOW;
        JwtService jwt = service(Duration.ofMinutes(15), now);
        UUID sessionId = UUID.randomUUID();

        String token = jwt.issueAccessToken(42L, sessionId, 3);
        JwtService.AccessTokenClaims claims = jwt.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.authVersion()).isEqualTo(3);
        assertThat(claims.expiresAt()).isAfter(now);
        assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("过期令牌：TOKEN_EXPIRED")
    void expiredTokenIsRejected() {
        Instant now = NOW;
        JwtService jwt = service(Duration.ofSeconds(-1), now);
        String token = jwt.issueAccessToken(1L, UUID.randomUUID(), 0);

        assertThatThrownBy(() -> jwt.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("被篡改的令牌：TOKEN_INVALID")
    void tamperedTokenIsRejected() {
        Instant now = NOW;
        JwtService jwt = service(Duration.ofMinutes(15), now);
        String token = jwt.issueAccessToken(1L, UUID.randomUUID(), 0);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> jwt.parseAccessToken(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("其他密钥签发的令牌不被接受")
    void foreignSignatureIsRejected() {
        Instant now = NOW;
        JwtService issuer = new JwtService(new JwtProperties("labflow",
                "another-signing-key-with-enough-length-abcdefgh", Duration.ofMinutes(15), Duration.ofDays(7)),
                Clock.fixed(now, ZoneOffset.UTC));
        JwtService verifier = service(Duration.ofMinutes(15), now);
        String token = issuer.issueAccessToken(1L, UUID.randomUUID(), 0);

        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("过短的签名密钥直接拒绝启动")
    void shortSecretIsRejected() {
        JwtProperties properties = new JwtProperties("labflow", "too-short", Duration.ofMinutes(15),
                Duration.ofDays(7));
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new JwtService(properties, clock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("未配置密钥时随机生成，重启即失效，不把密钥写进仓库")
    void missingSecretIsGenerated() {
        JwtProperties properties = new JwtProperties("labflow", "", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtService jwt = new JwtService(properties, Clock.systemUTC());
        assertThat(jwt.issueAccessToken(1L, UUID.randomUUID(), 0)).isNotBlank();
    }
}
