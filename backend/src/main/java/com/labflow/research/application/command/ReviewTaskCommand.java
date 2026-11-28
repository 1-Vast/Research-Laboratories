package com.labflow.research.application.command;

import com.labflow.research.domain.ReviewDecision;

/** 验收或退回任务交付。 */
public record ReviewTaskCommand(ReviewDecision decision, String note, Integer version) {
}
