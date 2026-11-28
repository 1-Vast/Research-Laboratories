package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 提交任务交付请求。 */
public record SubmitTaskRequest(
        @NotBlank(message = "提交内容不能为空") @Size(max = 4000, message = "提交内容最多 4000 字") String content,
        @NotNull(message = "version 不能为空") Integer version) {
}
