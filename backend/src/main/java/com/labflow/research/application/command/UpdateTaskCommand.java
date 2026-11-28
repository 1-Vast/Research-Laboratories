package com.labflow.research.application.command;

import com.labflow.research.domain.Priority;

import java.time.Instant;

/** 修改任务（仅未开始状态可改）。 */
public record UpdateTaskCommand(String title, String description, Priority priority, Instant dueAt,
                                Integer version) {
}
