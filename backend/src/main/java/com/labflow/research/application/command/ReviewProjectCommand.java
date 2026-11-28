package com.labflow.research.application.command;

import com.labflow.research.domain.ReviewDecision;

/** 立项 / 结项审批命令。 */
public record ReviewProjectCommand(ReviewDecision decision, String reason, Integer version) {
}
