package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 取消任务请求，原因必填。 */
public record CancelTaskRequest(
        @NotBlank(message = "取消原因不能为空") @Size(max = 500, message = "取消原因最多 500 字") String reason,
        @NotNull(message = "version 不能为空") Integer version) {
}
