package com.labflow.identity.application;

import com.labflow.identity.application.command.CreateUserCommand;
import com.labflow.identity.application.command.UpdateUserCommand;
import com.labflow.identity.domain.PasswordPolicy;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;
import com.labflow.identity.domain.Usernames;
import com.labflow.identity.domain.repository.RefreshTokenRepository;
import com.labflow.identity.domain.repository.RoleRepository;
import com.labflow.identity.domain.repository.SessionRepository;
import com.labflow.identity.domain.repository.UserRepository;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户管理用例（仅 user:manage）。所有写操作在同一事务内完成，并保护“最后一个可用管理员”。
 */
@Service
public class UserAdminService {

    private static final String ADMIN_ROLE_CODE = "SYS_ADMIN";

    private final UserRepository users;
    private final RoleRepository roles;
    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;
    private final AuditRecorder audit;

    public UserAdminService(UserRepository users, RoleRepository roles, SessionRepository sessions,
                            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy, Clock clock, AuditRecorder audit) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<User> list(String keyword, int page, int size) {
        return users.search(keyword, page, size);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User create(CreateUserCommand command, Long actorId) {
        String username = Usernames.normalize(command.username());
        passwordPolicy.validate(command.rawPassword());
        if (users.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        Set<Role> assignedRoles = resolveRoles(command.roleCodes());
        Instant now = Instant.now(clock);
        User user = User.register(username, passwordEncoder.encode(command.rawPassword()), command.email(),
                command.displayName(), defaultRoleIfEmpty(assignedRoles), now);
        if (assignedRoles.size() > 1) {
            user.assignRoles(assignedRoles, now);
        }
        User saved = users.save(user);
        audit.record(actorId, "user.create", "user", saved.id(), AuditRecorder.RESULT_SUCCESS,
                "roles=" + saved.roleCodes());
        return saved;
    }

    @Transactional
    public User update(Long id, UpdateUserCommand command, Long actorId) {
        User user = get(id);
        Instant now = Instant.now(clock);
        user.updateProfile(command.displayName(), command.email(), now);
        User saved = users.save(user);
        audit.record(actorId, "user.update", "user", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public User setEnabled(Long id, boolean enabled, Long actorId) {
        User user = get(id);
        Instant now = Instant.now(clock);
        if (!enabled) {
            assertNotLastAdmin(user);
            user.disable(now);
            User saved = users.save(user);
            // 禁用后立即吊销其全部会话与刷新令牌，旧 access token 下一次请求即 401
            sessions.revokeAllByUserId(id, now);
            refreshTokens.revokeByUserId(id, now);
            audit.record(actorId, "user.disable", "user", id, AuditRecorder.RESULT_SUCCESS, null);
            return saved;
        }
        user.enable(now);
        User saved = users.save(user);
        audit.record(actorId, "user.enable", "user", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public User assignRoles(Long id, List<String> roleCodes, Long actorId) {
        User user = get(id);
        Set<Role> newRoles = resolveRoles(roleCodes);
        if (newRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户至少需要一个角色");
        }
        boolean removesAdmin = user.hasRole(ADMIN_ROLE_CODE)
                && newRoles.stream().noneMatch(role -> role.code().equals(ADMIN_ROLE_CODE));
        if (removesAdmin) {
            assertNotLastAdmin(user);
        }
        Instant now = Instant.now(clock);
        user.assignRoles(newRoles, now);
        User saved = users.save(user);
        audit.record(actorId, "user.assignRoles", "user", id, AuditRecorder.RESULT_SUCCESS,
                "roles=" + saved.roleCodes());
        return saved;
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        User user = get(id);
        assertNotLastAdmin(user);
        Instant now = Instant.now(clock);
        user.disable(now);
        users.save(user);
        sessions.revokeAllByUserId(id, now);
        refreshTokens.revokeByUserId(id, now);
        audit.record(actorId, "user.delete", "user", id, AuditRecorder.RESULT_SUCCESS, "logical-delete");
    }

    private void assertNotLastAdmin(User user) {
        if (!user.hasRole(ADMIN_ROLE_CODE) || !user.enabled()) {
            return;
        }
        if (users.countOtherEnabledUsersWithRole(user.id(), ADMIN_ROLE_CODE) == 0) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
    }

    private Set<Role> resolveRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Role> found = roles.findByCodes(new LinkedHashSet<>(roleCodes));
        if (found.size() != new LinkedHashSet<>(roleCodes).size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在未知角色编码");
        }
        return new LinkedHashSet<>(found);
    }

    private Role defaultRoleIfEmpty(Set<Role> assigned) {
        if (!assigned.isEmpty()) {
            return assigned.iterator().next();
        }
        return roles.findByCode(User.DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "默认角色缺失"));
    }
}
