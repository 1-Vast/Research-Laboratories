package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** project_reviews 表的 JPA 映射；只增不改。 */
@Entity
@Table(name = "project_reviews")
public class ProjectReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 16)
    private ProjectReview.Type reviewType;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewDecision decision;

    @Column(length = 1000)
    private String reason;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectReviewJpaEntity() {
    }

    public static ProjectReviewJpaEntity of(Long projectId, ProjectReview.Type reviewType, Long reviewerId,
                                            ReviewDecision decision, String reason, Instant reviewedAt) {
        ProjectReviewJpaEntity entity = new ProjectReviewJpaEntity();
        entity.projectId = projectId;
        entity.reviewType = reviewType;
        entity.reviewerId = reviewerId;
        entity.decision = decision;
        entity.reason = reason;
        entity.reviewedAt = reviewedAt;
        entity.createdAt = reviewedAt;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public ProjectReview.Type getReviewType() {
        return reviewType;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
