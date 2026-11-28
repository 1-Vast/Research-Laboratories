package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

/** 只带版本号的命令请求（提交立项、重新编辑）。 */
public record VersionRequest(@NotNull(message = "version 不能为空") Integer version) {
}
