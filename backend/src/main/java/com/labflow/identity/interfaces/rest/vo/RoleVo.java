package com.labflow.identity.interfaces.rest.vo;

import java.time.Instant;
import java.util.List;

public record RoleVo(
        Long id,
        String code,
        String name,
        String description,
        boolean protectedRole,
        List<String> permissions,
        Instant createdAt
) {
}
