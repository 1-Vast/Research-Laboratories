package com.labflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 认证完全由自定义 JWT 过滤器链承担，排除默认的内存用户自动配置，
 * 避免启动时生成一个用不到却可能被误用的默认账号。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableAsync
public class LabFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabFlowApplication.class, args);
    }
}
