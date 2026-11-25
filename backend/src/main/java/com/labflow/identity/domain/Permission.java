package com.labflow.identity.domain;

/** 权限：一个可被 @PreAuthorize 校验的接口操作码，例如 project:update。 */
public record Permission(Long id, String code, String description) {
}
