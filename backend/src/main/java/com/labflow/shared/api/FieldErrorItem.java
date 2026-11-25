package com.labflow.shared.api;

/** 字段级校验错误，随 400 响应返回，避免前端只能看到笼统提示。 */
public record FieldErrorItem(String field, String message) {
}
