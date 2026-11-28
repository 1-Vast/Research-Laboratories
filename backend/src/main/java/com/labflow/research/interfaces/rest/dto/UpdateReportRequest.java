package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 修改周报正文请求。 */
public record UpdateReportRequest(
        @Size(max = 4000, message = "本周工作最多 4000 字") String work,
        @Size(max = 2000, message = "阻塞项最多 2000 字") String blockers,
        @Size(max = 2000, message = "下周计划最多 2000 字") String nextPlan,
        List<Long> taskIds,
        @NotNull(message = "version 不能为空") Integer version) {
}
