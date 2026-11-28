package com.labflow.research.domain;

import java.time.Instant;

/**
 * 审批轨迹（立项 / 结项）。只增不改，用于追溯“谁在什么时候批准或驳回、理由是什么”。
 */
public class ProjectReview {

    /** 审批类型。 */
    public enum Type {
        INIT,
        CLOSE
    }

    private final Long id;
    private final Long projectId;
    private final Type reviewType;
    private final Long reviewerId;
    private final ReviewDecision decision;
    private final String reason;
    private final Instant reviewedAt;

    private ProjectReview(Long id, Long projectId, Type reviewType, Long reviewerId,
                          ReviewDecision decision, String reason, Instant reviewedAt) {
        this.id = id;
        this.projectId = projectId;
        this.reviewType = reviewType;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.reason = reason;
        this.reviewedAt = reviewedAt;
    }

    public static ProjectReview record(Long projectId, Type reviewType, Long reviewerId,
                                       ReviewDecision decision, String reason, Instant reviewedAt) {
        return new ProjectReview(null, projectId, reviewType, reviewerId, decision, reason, reviewedAt);
    }

    public static ProjectReview restore(Long id, Long projectId, Type reviewType, Long reviewerId,
                                        ReviewDecision decision, String reason, Instant reviewedAt) {
        return new ProjectReview(id, projectId, reviewType, reviewerId, decision, reason, reviewedAt);
    }

    public Long id() {
        return id;
    }

    public Long projectId() {
        return projectId;
    }

    public Type reviewType() {
        return reviewType;
    }

    public Long reviewerId() {
        return reviewerId;
    }

    public ReviewDecision decision() {
        return decision;
    }

    public String reason() {
        return reason;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }
}
