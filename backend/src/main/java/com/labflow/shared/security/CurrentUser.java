package com.labflow.shared.security;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 SecurityContext 取出当前认证主体；未认证时抛 401。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthPrincipal require() {
        AuthPrincipal principal = orNull();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    public static Long requireUserId() {
        return require().userId();
    }

    /** 未认证场景（如过滤器层拒绝）返回 null，不抛异常。 */
    public static AuthPrincipal orNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
