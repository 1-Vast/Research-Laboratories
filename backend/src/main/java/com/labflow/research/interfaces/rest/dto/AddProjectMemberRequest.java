package com.labflow.research.interfaces.rest.dto;

import com.labflow.research.domain.ProjectMember;
import jakarta.validation.constraints.NotNull;

/** 添加项目成员请求；不传 memberType 时默认为 MEMBER。 */
public record AddProjectMemberRequest(
        @NotNull(message = "userId 不能为空") Long userId,
        ProjectMember.Type memberType) {
}
