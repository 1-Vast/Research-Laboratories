package com.labflow.identity.interfaces.rest.vo;

import java.time.Instant;
import java.util.List;

/** 用户对外视图：绝不包含 password_hash 字段。 */
public record UserVo(
        Long id,
        String username,
        String displayName,
        String email,
        boolean enabled,
        List<RoleBriefVo> roles,
        Instant createdAt
) {
}
