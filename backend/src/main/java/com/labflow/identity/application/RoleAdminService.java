package com.labflow.identity.application;

import com.labflow.identity.application.command.CreateRoleCommand;
import com.labflow.identity.application.command.UpdateRoleCommand;
import com.labflow.identity.domain.Permission;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.repository.PermissionRepository;
import com.labflow.identity.domain.repository.RoleRepository;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 角色与权限管理用例（仅 role:manage）。
 * <p>权限集合保存在数据库，认证时每次请求重新读取，因此授权可动态生效且不写死在代码里。</p>
 */
@Service
public class RoleAdminService {

    private static final Set<String> PLATFORM_CRITICAL_PERMISSIONS = Set.of("user:manage", "role:manage");
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "code", "name", "createdAt", "updatedAt");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final Clock clock;
    private final AuditRecorder audit;

    public RoleAdminService(RoleRepository roles, PermissionRepository permissions, Clock clock,
                            AuditRecorder audit) {
        this.roles = roles;
        this.permissions = permissions;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<Role> list(String keyword, int page, int size, String sort) {
        SortSpec sortSpec = SortSpec.parse(sort, SORTABLE_FIELDS, "id");
        return roles.search(keyword, page, size, sortSpec);
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        return roles.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissions.findAll();
    }

    @Transactional
    public Role create(CreateRoleCommand command, Long actorId) {
        String code = command.code() == null ? "" : command.code().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "角色编码需为 3-64 位大写字母、数字或下划线，且以字母开头");
        }
        if (roles.existsByCode(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ROLE_CODE);
        }
        Instant now = Instant.now(clock);
        Role saved = roles.save(Role.create(code, command.name(), command.description(), now));
        audit.record(actorId, "role.create", "role", saved.id(), AuditRecorder.RESULT_SUCCESS, "code=" + code);
        return saved;
    }

    @Transactional
    public Role update(Long id, UpdateRoleCommand command, Long actorId) {
        Role role = get(id);
        role.rename(command.name(), command.description(), Instant.now(clock));
        Role saved = roles.save(role);
        audit.record(actorId, "role.update", "role", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        Role role = get(id);
        role.ensureDeletable();
        if (roles.countAssignedUsers(id) > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        roles.delete(role);
        audit.record(actorId, "role.delete", "role", id, AuditRecorder.RESULT_SUCCESS, "code=" + role.code());
    }

    @Transactional
    public Role assignPermissions(Long id, List<String> permissionCodes, Long actorId) {
        Role role = get(id);
        Set<String> requested = new LinkedHashSet<>(permissionCodes == null ? List.of() : permissionCodes);
        List<Permission> found = permissions.findByCodes(requested);
        if (found.size() != requested.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在未知权限编码");
        }
        if (role.protectedRole() && !requested.containsAll(PLATFORM_CRITICAL_PERMISSIONS)) {
            // 内置管理员角色必须保留平台管理权限，避免把系统锁死
            throw new BusinessException(ErrorCode.CONFLICT, "内置管理员角色必须保留 user:manage 与 role:manage 权限");
        }
        role.replacePermissions(found, Instant.now(clock));
        Role saved = roles.save(role);
        audit.record(actorId, "role.assignPermissions", "role", id, AuditRecorder.RESULT_SUCCESS,
                "permissions=" + found.size());
        return saved;
    }
}
