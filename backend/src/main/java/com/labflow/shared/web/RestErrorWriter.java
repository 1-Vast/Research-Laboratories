package com.labflow.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 过滤器层写入统一错误响应（认证失败发生在 Controller 之前，走不到全局异常处理器）。 */
@Component
public class RestErrorWriter {

    private final ObjectMapper objectMapper;

    public RestErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.failure(code.name(), message, null, TraceContext.currentTraceId());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
