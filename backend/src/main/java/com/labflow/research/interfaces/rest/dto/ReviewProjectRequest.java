package com.labflow.research.interfaces.rest.dto;

import com.labflow.research.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 立项 / 结项审批请求。 */
public record ReviewProjectRequest(
        @NotNull(message = "decision 不能为空") ReviewDecision decision,
        @Size(max = 1000, message = "审批意见最多 1000 字") String reason,
        @NotNull(message = "version 不能为空") Integer version) {
}
