package com.labflow.identity.application;

import com.labflow.identity.application.command.LoginCommand;
import com.labflow.identity.application.command.RegisterCommand;
import com.labflow.identity.domain.AuthSession;
import com.labflow.identity.domain.PasswordPolicy;
import com.labflow.identity.domain.Permission;
import com.labflow.identity.domain.RefreshToken;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;
import com.labflow.identity.domain.repository.RefreshTokenRepository;
import com.labflow.identity.domain.repository.RoleRepository;
import com.labflow.identity.domain.repository.SessionRepository;
import com.labflow.identity.domain.repository.UserRepository;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.security.JwtProperties;
import com.labflow.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("认证用例（单元测试）")
class AuthServiceTest {

    private static final Instant NOW = Instant.now();

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final LoginThrottle loginThrottle = new LoginThrottle(Clock.fixed(NOW, ZoneOffset.UTC));
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtProperties jwtProperties = new JwtProperties("labflow",
            "unit-test-signing-key-that-is-long-enough-0123456789", Duration.ofMinutes(15), Duration.ofDays(7));
    private final JwtService jwtService = new JwtService(jwtProperties, Clock.fixed(NOW, ZoneOffset.UTC));

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, roles, sessions, refreshTokens, encoder, new PasswordPolicy(8, 64),
                jwtService, jwtProperties, Clock.fixed(NOW, ZoneOffset.UTC), audit, loginThrottle);
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roles.findByCode("RESEARCHER")).thenReturn(Optional.of(researcherRole()));
    }

    @Test
    @DisplayName("注册：密码以 BCrypt 存储，且不等于明文")
    void registerHashesPassword() {
        when(users.existsByUsername("bob")).thenReturn(false);
        when(users.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return User.restore(9L, user.username(), user.passwordHash(), user.email(), user.displayName(),
                    user.enabled(), user.authVersion(), user.createdAt(), user.updatedAt(), user.roles());
        });

        User saved = service.register(new RegisterCommand("Bob", "Passw0rd1", "bob@labflow.test", "Bob"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("bob");
        assertThat(captor.getValue().passwordHash()).isNotEqualTo("Passw0rd1");
        assertThat(encoder.matches("Passw0rd1", captor.getValue().passwordHash())).isTrue();
        assertThat(saved.roleCodes()).containsExactly("RESEARCHER");
    }

    @Test
    @DisplayName("注册重名：DUPLICATE_USERNAME，且不落库")
    void registerRejectsDuplicate() {
        when(users.existsByUsername("bob")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterCommand("bob", "Passw0rd1", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("登录成功：创建会话、签发访问令牌与一次性刷新令牌")
    void loginCreatesSessionAndTokens() {
        User user = activeUser();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        AuthResult result = service.login(new LoginCommand("Alice", "Passw0rd1", "127.0.0.1", "JUnit"));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        assertThat(jwtService.parseAccessToken(result.accessToken()).userId()).isEqualTo(1L);

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessions).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().userId()).isEqualTo(1L);
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("登录失败：密码错误、用户不存在、账号禁用返回同一错误码")
    void loginFailsUniformly() {
        User disabled = User.restore(2L, "carol", encoder.encode("Passw0rd1"), null, null, false, 0,
                NOW, NOW, Set.of(researcherRole()));
        when(users.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(users.findByUsername("carol")).thenReturn(Optional.of(disabled));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        for (LoginCommand command : new LoginCommand[]{
                new LoginCommand("alice", "WrongPassw0rd1", null, null),
                new LoginCommand("ghost", "Passw0rd1", null, null),
                new LoginCommand("carol", "Passw0rd1", null, null)}) {
            assertThatThrownBy(() -> service.login(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).code())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("退出：吊销当前会话与其刷新令牌")
    void logoutRevokesSession() {
        service.logout(java.util.UUID.randomUUID(), 1L);
        verify(refreshTokens).revokeBySessionId(any(), any());
        verify(audit).record(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("刷新令牌重放：吊销整个会话族并拒绝")
    void refreshReplayRevokesFamily() {
        RefreshToken used = RefreshToken.restore(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                com.labflow.shared.security.TokenHashing.sha256Hex("replayed"), NOW.plusSeconds(600),
                NOW.minusSeconds(30), null, NOW.minusSeconds(60));
        when(refreshTokens.findByTokenHash(com.labflow.shared.security.TokenHashing.sha256Hex("replayed")))
                .thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.refresh("replayed"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.SESSION_REVOKED);
        verify(refreshTokens).revokeBySessionId(used.sessionId(), NOW);
    }

    private User activeUser() {
        return User.restore(1L, "alice", encoder.encode("Passw0rd1"), "alice@labflow.test", "Alice", true, 0,
                NOW, NOW, Set.of(researcherRole()));
    }

    private Role researcherRole() {
        Permission permission = new Permission(1L, "task:read", "查看任务");
        return Role.restore(4L, "RESEARCHER", "科研人员", "默认角色", true, NOW, NOW, Set.of(permission));
    }
}
