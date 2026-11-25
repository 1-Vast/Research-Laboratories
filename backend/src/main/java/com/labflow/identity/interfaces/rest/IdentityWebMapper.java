package com.labflow.identity.interfaces.rest;

import com.labflow.identity.domain.Permission;
import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;
import com.labflow.identity.interfaces.rest.vo.MeVo;
import com.labflow.identity.interfaces.rest.vo.PermissionVo;
import com.labflow.identity.interfaces.rest.vo.RoleBriefVo;
import com.labflow.identity.interfaces.rest.vo.RoleVo;
import com.labflow.identity.interfaces.rest.vo.UserVo;

import java.util.Comparator;
import java.util.List;

/** 领域对象 → 对外视图。接口层只做映射，不写业务判断。 */
public final class IdentityWebMapper {

    private IdentityWebMapper() {
    }

    public static UserVo toUserVo(User user) {
        return new UserVo(user.id(), user.username(), user.displayName(), user.email(), user.enabled(),
                toRoleBriefs(user), user.createdAt());
    }

    public static MeVo toMeVo(User user) {
        return new MeVo(user.id(), user.username(), user.displayName(), user.email(), user.enabled(),
                user.authVersion(), toRoleBriefs(user),
                user.permissionCodes().stream().sorted().toList());
    }

    public static List<RoleBriefVo> toRoleBriefs(User user) {
        return user.roles().stream()
                .sorted(Comparator.comparing(Role::code))
                .map(role -> new RoleBriefVo(role.code(), role.name()))
                .toList();
    }

    public static RoleVo toRoleVo(Role role) {
        return new RoleVo(role.id(), role.code(), role.name(), role.description(), role.protectedRole(),
                role.permissions().stream().map(Permission::code).sorted().toList(), role.createdAt());
    }

    public static PermissionVo toPermissionVo(Permission permission) {
        return new PermissionVo(permission.id(), permission.code(), permission.description());
    }
}
