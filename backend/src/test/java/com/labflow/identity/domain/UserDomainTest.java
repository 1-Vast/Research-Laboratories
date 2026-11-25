package com.labflow.identity.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("用户聚合")
class UserDomainTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private Permission permission(String code) {
        return new Permission(1L, code, code);
    }

    private Role role(String code, String... permissionCodes) {
        Set<Permission> permissions = new java.util.LinkedHashSet<>();
        long id = 1;
        for (String permissionCode : permissionCodes) {
            permissions.add(new Permission(id++, permissionCode, permissionCode));
        }
        return Role.restore(id, code, code, code, true, now, now, permissions);
    }

    private User newUser() {
        return User.register("alice", "hash", "alice@labflow.test", "Alice", role("RESEARCHER", "task:read"), now);
    }

    @Test
    @DisplayName("注册：默认启用、版本为 0、绑定默认角色")
    void registerInitializesState() {
        User user = newUser();
        assertThat(user.enabled()).isTrue();
        assertThat(user.authVersion()).isZero();
        assertThat(user.roleCodes()).containsExactly("RESEARCHER");
        assertThat(user.permissionCodes()).containsExactly("task:read");
        assertThat(user.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("禁用：auth_version 递增，旧令牌随后失效")
    void disableBumpsAuthVersion() {
        User user = newUser();
        user.disable(now.plusSeconds(10));
        assertThat(user.enabled()).isFalse();
        assertThat(user.authVersion()).isEqualTo(1);

        user.enable(now.plusSeconds(20));
        assertThat(user.enabled()).isTrue();
        assertThat(user.authVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("改密：递增 auth_version")
    void changePasswordBumpsAuthVersion() {
        User user = newUser();
        user.changePassword("new-hash", now.plusSeconds(5));
        assertThat(user.passwordHash()).isEqualTo("new-hash");
        assertThat(user.authVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("多角色：权限取并集，判断与角色编码同时可用")
    void rolesUnionPermissions() {
        User user = newUser();
        user.assignRoles(Set.of(role("RESEARCHER", "task:read"),
                role("PROJECT_LEADER", "task:review", "project:update")), now);

        assertThat(user.hasRole("PROJECT_LEADER")).isTrue();
        assertThat(user.hasPermission("task:review")).isTrue();
        assertThat(user.hasPermission("project:update")).isTrue();
        assertThat(user.hasPermission("user:manage")).isFalse();
    }

    @Test
    @DisplayName("角色集合对外只读")
    void rolesAreReadOnly() {
        User user = newUser();
        assertThatThrownBy(() -> user.roles().add(role("PI")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("受保护角色不可改名或删除")
    void protectedRoleIsImmutable() {
        Role role = role("SYS_ADMIN", "user:manage");
        assertThatThrownBy(() -> role.rename("新名字", null, now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.PROTECTED_ROLE);
        assertThatThrownBy(role::ensureDeletable).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("权限替换后立即反映在权限集合上")
    void replacingPermissionsUpdatesCodes() {
        Role role = role("RESEARCHER", "task:read");
        role.replacePermissions(List.of(permission("report:read")), now);
        assertThat(role.hasPermission("task:read")).isFalse();
        assertThat(role.hasPermission("report:read")).isTrue();
    }
}
