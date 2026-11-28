package com.labflow.research.interfaces.rest.dto;

import com.labflow.research.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 任务验收请求。 */
public record ReviewTaskRequest(
        @NotNull(message = "decision 不能为空") ReviewDecision decision,
        @Size(max = 1000, message = "验收意见最多 1000 字") String note,
        @NotNull(message = "version 不能为空") Integer version) {
}
