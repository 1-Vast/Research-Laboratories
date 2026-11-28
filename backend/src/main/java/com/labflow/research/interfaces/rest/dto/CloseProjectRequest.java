package com.labflow.research.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 申请结项请求。 */
public record CloseProjectRequest(
        @Size(max = 2000, message = "结项说明最多 2000 字") String closeNote,
        @NotNull(message = "version 不能为空") Integer version) {
}
