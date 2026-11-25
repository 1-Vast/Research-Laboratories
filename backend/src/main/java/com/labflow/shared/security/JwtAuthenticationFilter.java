package com.labflow.shared.security;

import com.labflow.identity.application.AccessTokenAuthenticator;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.web.RestErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 解析 Authorization: Bearer <JWT>，校验通过后把主体写入 SecurityContext。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenAuthenticator authenticator;
    private final RestErrorWriter errorWriter;

    public JwtAuthenticationFilter(AccessTokenAuthenticator authenticator, RestErrorWriter errorWriter) {
        this.authenticator = authenticator;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            errorWriter.write(response, com.labflow.shared.error.ErrorCode.TOKEN_INVALID, "认证头格式不正确");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AuthPrincipal principal = authenticator.authenticate(token);
            List<SimpleGrantedAuthority> authorities = principal.authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, ex.code(), ex.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
