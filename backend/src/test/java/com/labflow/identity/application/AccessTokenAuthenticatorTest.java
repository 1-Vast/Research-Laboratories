package com.labflow.identity.application;

import com.labflow.identity.domain.AuthSession;
import com.labflow.identity.domain.Permission;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;
import com.labflow.identity.domain.repository.SessionRepository;
import com.labflow.identity.domain.repository.UserRepository;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.security.AuthPrincipal;
import com.labflow.shared.security.JwtProperties;
import com.labflow.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("请求级认证（单元测试）")
class AccessTokenAuthenticatorTest {

    private static final Instant NOW = Instant.now();

    private final UserRepository users = mock(UserRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final JwtProperties jwtProperties = new JwtProperties("labflow",
            "unit-test-signing-key-that-is-long-enough-0123456789", Duration.ofMinutes(15), Duration.ofDays(7));
    private final JwtService jwtService = new JwtService(jwtProperties, Clock.fixed(NOW, ZoneOffset.UTC));

    private AccessTokenAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new AccessTokenAuthenticator(jwtService, users, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("有效令牌：返回用户名、会话与数据库中的权限集合")
    void authenticatesValidToken() {
        UUID sessionId = UUID.randomUUID();
        User user = user(1L, true, 0);
        AuthSession session = session(sessionId, 1L, 0, null);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(users.findById(1L)).thenReturn(Optional.of(user));

        AuthPrincipal principal = authenticator.authenticate(jwtService.issueAccessToken(1L, sessionId, 0));

        assertThat(principal.userId()).isEqualTo(1L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.sessionId()).isEqualTo(sessionId);
        assertThat(principal.authorities()).containsExactly("task:read");
    }

    @Test
    @DisplayName("会话已吊销：SESSION_REVOKED（退出登录后旧令牌不可用）")
    void rejectsRevokedSession() {
        UUID sessionId = UUID.randomUUID();
        AuthSession revoked = session(sessionId, 1L, 0, NOW.minusSeconds(30));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(revoked));

        String token = jwtService.issueAccessToken(1L, sessionId, 0);
        assertThatThrownBy(() -> authenticator.authenticate(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.SESSION_REVOKED);
    }

    @Test
    @DisplayName("会话不存在：TOKEN_INVALID")
    void rejectsUnknownSession() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.empty());

        String token = jwtService.issueAccessToken(1L, sessionId, 0);
        assertThatThrownBy(() -> authenticator.authenticate(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("授权版本不一致：SESSION_REVOKED（改密或撤权后旧令牌失效）")
    void rejectsOutdatedAuthVersion() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(sessionId, 1L, 0, null)));
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, true, 5)));

        String token = jwtService.issueAccessToken(1L, sessionId, 0);
        assertThatThrownBy(() -> authenticator.authenticate(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.SESSION_REVOKED);
    }

    @Test
    @DisplayName("账号被禁用：ACCOUNT_DISABLED")
    void rejectsDisabledAccount() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(sessionId, 1L, 0, null)));
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, false, 0)));

        String token = jwtService.issueAccessToken(1L, sessionId, 0);
        assertThatThrownBy(() -> authenticator.authenticate(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    private AuthSession session(UUID id, Long userId, int authVersion, Instant revokedAt) {
        return AuthSession.restore(id, userId, authVersion, NOW.minusSeconds(60), NOW.plusSeconds(3600),
                revokedAt, NOW.minusSeconds(10), "127.0.0.1", "JUnit");
    }

    private User user(Long id, boolean enabled, int authVersion) {
        Permission permission = new Permission(1L, "task:read", "查看任务");
        Role role = Role.restore(4L, "RESEARCHER", "科研人员", "默认角色", true, NOW, NOW, Set.of(permission));
        return User.restore(id, "alice", "hash", "alice@labflow.test", "Alice", enabled, authVersion, NOW, NOW,
                Set.of(role));
    }
}
