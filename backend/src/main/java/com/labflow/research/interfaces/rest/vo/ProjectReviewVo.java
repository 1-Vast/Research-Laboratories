package com.labflow.research.interfaces.rest.vo;

import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.ReviewDecision;

import java.time.Instant;

/** 审批轨迹返回体。 */
public record ProjectReviewVo(Long id, Long projectId, ProjectReview.Type reviewType, Long reviewerId,
                              ReviewDecision decision, String reason, Instant reviewedAt) {
}
