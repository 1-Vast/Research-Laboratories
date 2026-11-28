package com.labflow.research.interfaces.rest.vo;

import com.labflow.research.domain.Priority;
import com.labflow.research.domain.TaskStatus;

import java.time.Instant;

/** 任务返回体。 */
public record TaskVo(Long id, Long projectId, Long assigneeId, String title, String description,
                     TaskStatus status, Priority priority, Instant dueAt, String cancelReason,
                     int version, Instant createdAt, Instant updatedAt) {
}
