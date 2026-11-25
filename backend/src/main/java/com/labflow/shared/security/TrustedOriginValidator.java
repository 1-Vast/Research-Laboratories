package com.labflow.shared.security;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 校验携带 Cookie 的接口（刷新、退出）的请求来源。
 * <p>刷新令牌放在 HttpOnly Cookie 中，浏览器会自动携带；若不做来源校验，
 * 第三方站点可以诱导用户浏览器发起请求（CSRF）。此处校验 Origin/Referer 白名单作为纵深防御，
 * 与 SameSite=Lax 共同生效。没有 Origin/Referer 的场景（curl、服务端调用）不携带 Cookie 也能工作。</p>
 */
@Component
public class TrustedOriginValidator {

    private final List<String> allowedOrigins;

    public TrustedOriginValidator(SecurityProperties properties) {
        this.allowedOrigins = properties.allowedOrigins().stream()
                .map(origin -> origin.toLowerCase(Locale.ROOT).replaceAll("/+$", ""))
                .toList();
    }

    public void assertTrusted(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            origin = originOf(request.getHeader("Referer"));
        }
        if (origin == null) {
            // 非浏览器客户端（无 Origin/Referer）无法进行 CSRF，交由 Cookie 与会话校验
            return;
        }
        String normalized = origin.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        if (!allowedOrigins.contains(normalized)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求来源不受信任");
        }
    }

    private String originOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                origin.append(':').append(uri.getPort());
            }
            return origin.toString();
        } catch (IllegalArgumentException ex) {
            return "invalid";
        }
    }
}
