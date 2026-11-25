package com.labflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "labflow.jwt")
public record JwtProperties(
        String issuer,
        String signingKey,
        Duration accessTtl,
        Duration refreshTtl
) {
}
