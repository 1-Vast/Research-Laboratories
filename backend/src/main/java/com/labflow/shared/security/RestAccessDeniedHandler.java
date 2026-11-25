package com.labflow.shared.security;

import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.web.RestErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 已认证但无权限：403，并以独立事务记录越权审计。 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final RestErrorWriter errorWriter;
    private final AuditRecorder audit;

    public RestAccessDeniedHandler(RestErrorWriter errorWriter, AuditRecorder audit) {
        this.errorWriter = errorWriter;
        this.audit = audit;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        recordDenied(request);
        errorWriter.write(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }

    private void recordDenied(HttpServletRequest request) {
        try {
            AuthPrincipal principal = CurrentUser.orNull();
            audit.recordIndependent(principal == null ? null : principal.userId(),
                    "security.access.denied", "endpoint", request.getRequestURI(),
                    AuditRecorder.RESULT_DENIED, "method=" + request.getMethod());
        } catch (RuntimeException auditFailure) {
            log.warn("无法写入越权访问审计: {}", auditFailure.getMessage());
        }
    }
}
