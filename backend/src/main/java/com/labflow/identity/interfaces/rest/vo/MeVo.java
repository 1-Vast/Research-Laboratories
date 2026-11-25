package com.labflow.identity.interfaces.rest.vo;

import java.util.List;

/** 当前用户视图：供前端渲染菜单，也用于演示“权限变更后重新登录/刷新即生效”。 */
public record MeVo(
        Long id,
        String username,
        String displayName,
        String email,
        boolean enabled,
        int authVersion,
        List<RoleBriefVo> roles,
        List<String> permissions
) {
}
