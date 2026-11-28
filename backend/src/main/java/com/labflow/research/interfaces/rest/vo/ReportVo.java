package com.labflow.research.interfaces.rest.vo;

import com.labflow.research.domain.ReportStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 周报返回体；taskIds 为关联任务。 */
public record ReportVo(Long id, Long projectId, Long authorId, LocalDate weekStart, String work,
                       String blockers, String nextPlan, ReportStatus status, Long reviewerId,
                       String reviewNote, int version, Instant createdAt, Instant updatedAt,
                       List<Long> taskIds) {
}
