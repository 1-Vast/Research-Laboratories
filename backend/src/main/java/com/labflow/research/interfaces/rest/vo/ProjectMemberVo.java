package com.labflow.research.interfaces.rest.vo;

import com.labflow.research.domain.ProjectMember;

import java.time.Instant;

/** 项目成员返回体。 */
public record ProjectMemberVo(Long userId, ProjectMember.Type memberType, boolean active, Instant joinedAt) {
}
