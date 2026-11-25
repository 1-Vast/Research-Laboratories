package com.labflow.identity.application;

import com.labflow.identity.domain.AuthSession;
import com.labflow.identity.domain.User;
import com.labflow.identity.domain.repository.SessionRepository;
import com.labflow.identity.domain.repository.UserRepository;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.security.AuthPrincipal;
import com.labflow.shared.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 请求级认证：校验 JWT 签名与过期 → 校验服务端会话未吊销 → 校验账号状态与 auth_version →
 * 从数据库加载当前权限集合。
 * <p>因此“退出登录后旧令牌立即 401”“权限修改后下一次请求即生效”都由这条链路保证。</p>
 */
@Service
public class AccessTokenAuthenticator {

    private final JwtService jwtService;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final Clock clock;

    public AccessTokenAuthenticator(JwtService jwtService, UserRepository users, SessionRepository sessions,
                                    Clock clock) {
        this.jwtService = jwtService;
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthPrincipal authenticate(String rawToken) {
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(rawToken);
        Instant now = Instant.now(clock);

        AuthSession session = sessions.findById(claims.sessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        if (session.revokedAt() != null) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED);
        }
        if (!session.expiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED);
        }
        if (!session.userId().equals(claims.userId())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        User user = users.findById(claims.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!user.enabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.authVersion() != claims.authVersion() || session.authVersion() != claims.authVersion()) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED);
        }

        return new AuthPrincipal(user.id(), user.username(), session.id(), user.authVersion(),
                user.permissionCodes());
    }
}
