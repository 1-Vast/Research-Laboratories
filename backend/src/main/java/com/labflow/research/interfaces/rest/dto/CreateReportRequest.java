package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 创建周报草稿请求；weekStart 传任意一天，服务端归一到周一。 */
public record CreateReportRequest(
        @NotNull(message = "weekStart 不能为空") LocalDate weekStart,
        @Size(max = 4000, message = "本周工作最多 4000 字") String work,
        @Size(max = 2000, message = "阻塞项最多 2000 字") String blockers,
        @Size(max = 2000, message = "下周计划最多 2000 字") String nextPlan,
        List<Long> taskIds) {
}
