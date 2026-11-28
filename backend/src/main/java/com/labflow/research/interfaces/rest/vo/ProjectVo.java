package com.labflow.research.interfaces.rest.vo;

import com.labflow.research.domain.ProjectStatus;

import java.time.Instant;
import java.time.LocalDate;

/** 项目列表 / 详情返回体。 */
public record ProjectVo(Long id, Long labId, Long leaderId, String name, String objective,
                        ProjectStatus status, LocalDate plannedStart, LocalDate plannedEnd, String closeNote,
                        int version, Instant createdAt, Instant updatedAt) {
}
