package com.labflow.shared.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh token 通过 HttpOnly Cookie 下发：前端脚本读不到，降低 XSS 窃取风险；
 * access token 只在响应体返回，由前端保存在内存。
 */
@Component
public class RefreshCookieSupport {

    private final SecurityProperties properties;
    private final JwtProperties jwtProperties;

    public RefreshCookieSupport(SecurityProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    public String cookieName() {
        return properties.refreshCookieName();
    }

    public ResponseCookie create(String rawToken) {
        return ResponseCookie.from(properties.refreshCookieName(), rawToken)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Lax")
                .path(properties.refreshCookiePath())
                .maxAge(jwtProperties.refreshTtl())
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(properties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Lax")
                .path(properties.refreshCookiePath())
                .maxAge(Duration.ZERO)
                .build();
    }
}
