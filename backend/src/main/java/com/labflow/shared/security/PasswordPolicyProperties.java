package com.labflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "labflow.password")
public record PasswordPolicyProperties(int minLength, int maxLength) {
}
