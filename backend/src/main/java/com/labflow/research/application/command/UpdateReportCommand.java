package com.labflow.research.application.command;

import java.util.List;

/** 修改周报正文；仅草稿或退回状态可改。 */
public record UpdateReportCommand(String work, String blockers, String nextPlan, List<Long> taskIds,
                                  Integer version) {
}
