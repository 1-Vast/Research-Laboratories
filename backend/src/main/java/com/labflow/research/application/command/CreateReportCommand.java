package com.labflow.research.application.command;

import java.time.LocalDate;
import java.util.List;

/** 创建周报草稿。weekStart 会被归一到所在周的周一。 */
public record CreateReportCommand(LocalDate weekStart, String work, String blockers, String nextPlan,
                                  List<Long> taskIds) {
}
