package com.labflow.research.interfaces.rest.vo;

import com.labflow.research.domain.TaskSubmission;

import java.time.Instant;

/** 任务交付快照返回体。 */
public record TaskSubmissionVo(Long id, Long taskId, Long authorId, int submissionNo, String content,
                               TaskSubmission.Status reviewStatus, Long reviewerId, String reviewNote,
                               Instant reviewedAt, Instant createdAt) {
}
