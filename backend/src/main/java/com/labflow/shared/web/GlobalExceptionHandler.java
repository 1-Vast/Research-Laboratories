package com.labflow.shared.web;

import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.api.FieldErrorItem;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理：任何异常都不向前端返回 Java 堆栈，只返回 {code,message,data,traceId}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.code();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.failure(code.name(), ex.getMessage(), null, TraceContext.currentTraceId()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<List<FieldErrorItem>>> handleValidation(BindException ex) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toItem)
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        errors, TraceContext.currentTraceId()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorItem>>> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        errors, TraceContext.currentTraceId()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.status())
                .body(ApiResponse.failure(ErrorCode.BAD_REQUEST.name(), "请求参数格式不正确", null,
                        TraceContext.currentTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ApiResponse.failure(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.defaultMessage(), null,
                        TraceContext.currentTraceId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status())
                .body(ApiResponse.failure(ErrorCode.UNAUTHENTICATED.name(), ErrorCode.UNAUTHENTICATED.defaultMessage(),
                        null, TraceContext.currentTraceId()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(ApiResponse.failure(ErrorCode.CONFLICT.name(), "数据约束冲突，操作未生效", null,
                        TraceContext.currentTraceId()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ApiResponse.failure(ErrorCode.NOT_FOUND.name(), ErrorCode.NOT_FOUND.defaultMessage(), null,
                        TraceContext.currentTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error, traceId={}", TraceContext.currentTraceId(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage(),
                        null, TraceContext.currentTraceId()));
    }

    private FieldErrorItem toItem(FieldError error) {
        String message = error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage();
        return new FieldErrorItem(error.getField(), message);
    }
}
