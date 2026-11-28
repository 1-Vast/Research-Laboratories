package com.labflow.research.application.command;

import com.labflow.research.domain.Priority;

import java.time.Instant;

/** 创建并分派任务。受派人必须是项目成员。 */
public record CreateTaskCommand(Long assigneeId, String title, String description, Priority priority,
                                Instant dueAt) {
}
