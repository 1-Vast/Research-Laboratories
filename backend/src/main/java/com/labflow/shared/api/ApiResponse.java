package com.labflow.shared.api;

/**
 * 统一接口返回格式：{code, message, data, traceId}。
 * code 为业务码（成功固定为 OK），HTTP 状态码另行表达语义。
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId);
    }

    public static ApiResponse<Void> ok(String traceId) {
        return new ApiResponse<>("OK", "success", null, traceId);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data, String traceId) {
        return new ApiResponse<>(code, message, data, traceId);
    }
}
