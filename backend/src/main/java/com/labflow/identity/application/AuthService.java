package com.labflow.identity.application;

import com.labflow.identity.application.command.LoginCommand;
import com.labflow.identity.application.command.RegisterCommand;
import com.labflow.identity.domain.AuthSession;
import com.labflow.identity.domain.PasswordPolicy;
import com.labflow.identity.domain.RefreshToken;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;
import com.labflow.identity.domain.Usernames;
import com.labflow.identity.domain.repository.RefreshTokenRepository;
import com.labflow.identity.domain.repository.RoleRepository;
import com.labflow.identity.domain.repository.SessionRepository;
import com.labflow.identity.domain.repository.UserRepository;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.security.JwtProperties;
import com.labflow.shared.security.JwtService;
import com.labflow.shared.security.TokenHashing;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 认证用例：注册、登录、刷新、退出。
 * <p>安全要点：密码 BCrypt 加盐哈希；登录失败统一提示（并执行等效校验以降低时序差异）；
 * 退出在服务端吊销会话；刷新令牌一次性使用，重放即吊销整个会话。</p>
 */
@Service
public class AuthService {

    /** 用于“用户不存在”路径的等价 BCrypt 校验，避免通过响应时间探测账号是否存在。 */
    private static final String DUMMY_HASH = "$2a$10$pD6CkUgmYNLTLqymNCOB/ujxhj6Hs3.mONUXjlW0cnYaTouUjBHtO";

    private final UserRepository users;
    private final RoleRepository roles;
    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final AuditRecorder audit;
    private final LoginThrottle loginThrottle;

    public AuthService(UserRepository users, RoleRepository roles, SessionRepository sessions,
                       RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, JwtService jwtService, JwtProperties jwtProperties,
                       Clock clock, AuditRecorder audit, LoginThrottle loginThrottle) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.audit = audit;
        this.loginThrottle = loginThrottle;
    }

    @Transactional
    public User register(RegisterCommand command) {
        String username = Usernames.normalize(command.username());
        passwordPolicy.validate(command.rawPassword());
        if (users.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        Role defaultRole = roles.findByCode(User.DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "默认角色缺失，请检查种子数据"));
        Instant now = Instant.now(clock);
        User user = User.register(username, passwordEncoder.encode(command.rawPassword()), command.email(),
                command.displayName(), defaultRole, now);
        User saved = users.save(user);
        audit.record(saved.id(), "auth.register", "user", saved.id(), AuditRecorder.RESULT_SUCCESS,
                "role=" + User.DEFAULT_ROLE_CODE);
        return saved;
    }

    @Transactional
    public AuthResult login(LoginCommand command) {
        String username = Usernames.normalize(command.username());
        // 先做失败次数检查：窗口内失败过多时，即使口令正确也先返回 429
        loginThrottle.checkAllowed(username, command.clientIp());

        User user = users.findByUsername(username).orElse(null);
        String hash = user == null ? DUMMY_HASH : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(command.rawPassword(), hash);
        if (user == null || !passwordMatches || !user.enabled()) {
            loginThrottle.recordFailure(username, command.clientIp());
            // 拒绝事件独立事务落库：外层抛异常回滚时，失败审计仍然保留
            audit.recordIndependent(null, "auth.login.failed", "user", null, AuditRecorder.RESULT_DENIED,
                    "username=" + username);
            // 统一提示，不泄露是“用户不存在”“密码错误”还是“账号禁用”
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginThrottle.reset(username, command.clientIp());

        Instant now = Instant.now(clock);
        AuthSession session = AuthSession.open(user.id(), user.authVersion(), now,
                jwtProperties.refreshTtl(), command.clientIp(), command.userAgent());
        AuthSession savedSession = sessions.save(session);

        String accessToken = jwtService.issueAccessToken(user.id(), savedSession.id(), user.authVersion());
        String rawRefreshToken = TokenHashing.newOpaqueToken();
        refreshTokens.save(RefreshToken.issue(savedSession.id(),
                TokenHashing.sha256Hex(rawRefreshToken), now, savedSession.expiresAt()));
        audit.record(user.id(), "auth.login", "session", savedSession.id(), AuditRecorder.RESULT_SUCCESS,
                "clientIp=" + command.clientIp());
        return new AuthResult(accessToken, jwtService.accessTtlSeconds(), rawRefreshToken, user);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokens.findByTokenHash(TokenHashing.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (token.isReplayed()) {
            // 复用检测：令牌已用过仍被重放，视为泄露，吊销整个会话族（需要提交，故不抛异常回滚）
            revokeSessionFamily(token.sessionId(), now);
            throw new BusinessException(ErrorCode.SESSION_REVOKED, "刷新令牌已失效，请重新登录");
        }
        if (!token.isUsable(now)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        AuthSession session = sessions.findById(token.sessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        if (!session.isActive(now)) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED);
        }
        User user = users.findById(session.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!user.enabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.authVersion() != session.authVersion()) {
            revokeSessionFamily(session.id(), now);
            throw new BusinessException(ErrorCode.SESSION_REVOKED);
        }

        token.markUsed(now);
        refreshTokens.save(token);
        session.touch(now);
        sessions.save(session);

        String newRawToken = TokenHashing.newOpaqueToken();
        refreshTokens.save(RefreshToken.issue(session.id(), TokenHashing.sha256Hex(newRawToken), now,
                session.expiresAt()));
        String accessToken = jwtService.issueAccessToken(user.id(), session.id(), user.authVersion());
        return new AuthResult(accessToken, jwtService.accessTtlSeconds(), newRawToken, user);
    }

    @Transactional
    public void logout(UUID sessionId, Long actorId) {
        Instant now = Instant.now(clock);
        sessions.findById(sessionId).ifPresent(session -> {
            session.revoke(now);
            sessions.save(session);
        });
        refreshTokens.revokeBySessionId(sessionId, now);
        audit.record(actorId, "auth.logout", "session", sessionId, AuditRecorder.RESULT_SUCCESS, null);
    }

    @Transactional(readOnly = true)
    public User currentUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 自助改密：校验旧密码 → 策略校验新密码 → 递增 auth_version → 吊销全部会话与刷新令牌。
     * <p>改密属于敏感操作，即使当前令牌也会失效，前端需要重新登录；否则旧设备上的会话仍可继续使用。</p>
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前密码不正确");
        }
        passwordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新密码不能与当前密码相同");
        }
        Instant now = Instant.now(clock);
        user.changePassword(passwordEncoder.encode(newPassword), now);
        users.save(user);
        sessions.revokeAllByUserId(userId, now);
        refreshTokens.revokeByUserId(userId, now);
        audit.record(userId, "auth.password.change", "user", userId, AuditRecorder.RESULT_SUCCESS, null);
    }

    private void revokeSessionFamily(UUID sessionId, Instant now) {
        sessions.findById(sessionId).ifPresent(session -> {
            session.revoke(now);
            sessions.save(session);
        });
        refreshTokens.revokeBySessionId(sessionId, now);
    }
}
