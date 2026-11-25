package com.labflow.shared.web;

import com.labflow.shared.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 访问日志：每次请求结束后输出一行（含 traceId、状态码、耗时与认证主体），
 * 便于把前端报错、审计记录与日志按 traceId 对齐。不记录请求体、令牌与查询串，避免敏感信息落盘。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - startedAt) / 1_000_000;
            var principal = CurrentUser.orNull();
            log.info("request completed method={} path={} status={} duration={}ms user={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), costMs,
                    principal == null ? "-" : principal.username());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui") || path.startsWith("/h2-console");
    }
}
