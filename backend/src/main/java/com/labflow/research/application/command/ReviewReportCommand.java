package com.labflow.research.application.command;

import com.labflow.research.domain.ReviewDecision;

/** 审核周报。 */
public record ReviewReportCommand(ReviewDecision decision, String note, Integer version) {
}
