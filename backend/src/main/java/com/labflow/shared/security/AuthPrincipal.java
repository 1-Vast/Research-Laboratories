package com.labflow.shared.security;

import java.util.Set;

/**
 * 请求级认证主体。每次请求都从数据库重新装载，权限变更下一次请求即生效。
 */
public record AuthPrincipal(
        Long userId,
        String username,
        java.util.UUID sessionId,
        int authVersion,
        Set<String> authorities
) {
    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }

    @Override
    public String toString() {
        return "AuthPrincipal[userId=" + userId + ", username=" + username + "]";
    }
}
