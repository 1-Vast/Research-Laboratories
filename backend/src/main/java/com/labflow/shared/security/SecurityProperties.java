package com.labflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "labflow.security")
public record SecurityProperties(
        String refreshCookieName,
        String refreshCookiePath,
        boolean refreshCookieSecure,
        List<String> allowedOrigins
) {
}
