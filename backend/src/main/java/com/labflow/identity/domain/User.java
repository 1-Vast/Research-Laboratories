package com.labflow.identity.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户聚合根。
 * <p>只包含身份与授权状态，不依赖任何框架类型；持久化由基础设施适配器负责。</p>
 */
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String displayName;
    private boolean enabled;
    private int authVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private final Set<Role> roles = new LinkedHashSet<>();

    private User() {
    }

    public static User register(String username, String passwordHash, String email, String displayName,
                                Role defaultRole, Instant now) {
        return register(username, passwordHash, email, displayName, Set.of(defaultRole), now);
    }

    public static User register(String username, String passwordHash, String email, String displayName,
                                Set<Role> initialRoles, Instant now) {
        User user = new User();
        user.username = username;
        user.passwordHash = passwordHash;
        user.email = email;
        user.displayName = displayName;
        user.enabled = true;
        user.authVersion = 0;
        user.createdAt = now;
        user.updatedAt = now;
        if (initialRoles != null) {
            user.roles.addAll(initialRoles);
        }
        return user;
    }

    /** 由持久化适配器重建聚合，不做业务校验。 */
    public static User restore(Long id, String username, String passwordHash, String email, String displayName,
                               boolean enabled, int authVersion, Instant createdAt, Instant updatedAt,
                               Set<Role> roles) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.passwordHash = passwordHash;
        user.email = email;
        user.displayName = displayName;
        user.enabled = enabled;
        user.authVersion = authVersion;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        user.roles.clear();
        if (roles != null) {
            user.roles.addAll(roles);
        }
        return user;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        // 改密后旧令牌立即失效：auth_version 与令牌声明不一致
        this.authVersion++;
        this.updatedAt = now;
    }

    public void updateProfile(String displayName, String email, Instant now) {
        this.displayName = displayName;
        this.email = email;
        this.updatedAt = now;
    }

    public void assignRoles(Set<Role> newRoles, Instant now) {
        this.roles.clear();
        if (newRoles != null) {
            this.roles.addAll(newRoles);
        }
        // 角色变化会让已有会话的权限快照失效，递增版本强制重新登录
        this.updatedAt = now;
    }

    public void enable(Instant now) {
        if (this.enabled) {
            return;
        }
        this.enabled = true;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.enabled = false;
        this.authVersion++;
        this.updatedAt = now;
    }

    public boolean hasPermission(String permissionCode) {
        return roles.stream()
                .flatMap(role -> role.permissions().stream())
                .anyMatch(permission -> permission.code().equals(permissionCode));
    }

    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(role -> role.code().equals(roleCode));
    }

    public Set<String> roleCodes() {
        return roles.stream().map(Role::code).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> permissionCodes() {
        return roles.stream()
                .flatMap(role -> role.permissions().stream())
                .map(Permission::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabled() {
        return enabled;
    }

    public int authVersion() {
        return authVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Set<Role> roles() {
        return Collections.unmodifiableSet(roles);
    }

    /** 领域常量：注册默认角色。 */
    public static final String DEFAULT_ROLE_CODE = "RESEARCHER";
}
