package com.labflow.research.interfaces.rest.dto;

import com.labflow.research.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 审核周报请求。 */
public record ReviewReportRequest(
        @NotNull(message = "decision 不能为空") ReviewDecision decision,
        @Size(max = 1000, message = "审核意见最多 1000 字") String note,
        @NotNull(message = "version 不能为空") Integer version) {
}
