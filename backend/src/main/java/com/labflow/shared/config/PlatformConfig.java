package com.labflow.shared.config;

import com.labflow.identity.domain.PasswordPolicy;
import com.labflow.shared.security.PasswordPolicyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PlatformConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordPolicy passwordPolicy(PasswordPolicyProperties properties) {
        return new PasswordPolicy(properties.minLength(), properties.maxLength());
    }
}
