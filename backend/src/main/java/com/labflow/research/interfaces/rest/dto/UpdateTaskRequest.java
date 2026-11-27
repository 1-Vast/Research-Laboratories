package com.labflow.research.interfaces.rest.dto;

import com.labflow.research.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 修改任务请求。 */
public record UpdateTaskRequest(
        @NotBlank(message = "任务标题不能为空") @Size(max = 150, message = "任务标题最多 150 字") String title,
        @Size(max = 4000, message = "任务描述最多 4000 字") String description,
        Priority priority,
        Instant dueAt,
        @NotNull(message = "version 不能为空") Integer version) {
}
