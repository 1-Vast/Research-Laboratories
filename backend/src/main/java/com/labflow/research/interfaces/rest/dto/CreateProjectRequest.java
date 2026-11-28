package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 创建项目草稿请求。 */
public record CreateProjectRequest(
        @NotNull(message = "labId 不能为空") Long labId,
        @NotBlank(message = "项目名称不能为空") @Size(max = 150, message = "项目名称最多 150 字") String name,
        @Size(max = 2000, message = "项目目标最多 2000 字") String objective,
        LocalDate plannedStart,
        LocalDate plannedEnd) {
}
