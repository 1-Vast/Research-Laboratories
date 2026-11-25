package com.labflow.identity.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 角色聚合：角色决定“具备哪类操作能力”，不承载任何实验室/项目范围。 */
public class Role {

    private Long id;
    private String code;
    private String name;
    private String description;
    private boolean protectedRole;
    private Instant createdAt;
    private Instant updatedAt;
    private final Set<Permission> permissions = new LinkedHashSet<>();

    private Role() {
    }

    public static Role create(String code, String name, String description, Instant now) {
        Role role = new Role();
        role.code = code;
        role.name = name;
        role.description = description;
        role.protectedRole = false;
        role.createdAt = now;
        role.updatedAt = now;
        return role;
    }

    public static Role restore(Long id, String code, String name, String description, boolean protectedRole,
                               Instant createdAt, Instant updatedAt, Set<Permission> permissions) {
        Role role = new Role();
        role.id = id;
        role.code = code;
        role.name = name;
        role.description = description;
        role.protectedRole = protectedRole;
        role.createdAt = createdAt;
        role.updatedAt = updatedAt;
        if (permissions != null) {
            role.permissions.addAll(permissions);
        }
        return role;
    }

    public void rename(String name, String description, Instant now) {
        ensureMutable();
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

    public void replacePermissions(Collection<Permission> newPermissions, Instant now) {
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
        this.updatedAt = now;
    }

    public void ensureDeletable() {
        ensureMutable();
    }

    private void ensureMutable() {
        if (protectedRole) {
            throw new BusinessException(ErrorCode.PROTECTED_ROLE);
        }
    }

    public Long id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean protectedRole() {
        return protectedRole;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Set<Permission> permissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.stream().anyMatch(p -> p.code().equals(permissionCode));
    }
}
