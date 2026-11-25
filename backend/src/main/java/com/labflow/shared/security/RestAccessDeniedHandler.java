package com.labflow.shared.security;

import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.web.RestErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 已认证但无权限：403。 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final RestErrorWriter errorWriter;

    public RestAccessDeniedHandler(RestErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }
}
