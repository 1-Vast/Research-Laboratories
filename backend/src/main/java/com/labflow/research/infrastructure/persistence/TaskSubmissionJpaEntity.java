package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.TaskSubmission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** task_submissions 表的 JPA 映射；每次提交一条，历史不覆盖。 */
@Entity
@Table(name = "task_submissions")
public class TaskSubmissionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "submission_no", nullable = false)
    private int submissionNo;

    @Column(nullable = false, length = 4000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private TaskSubmission.Status reviewStatus;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskSubmissionJpaEntity() {
    }

    public static TaskSubmissionJpaEntity of(Long taskId, Long authorId, int submissionNo, String content,
                                             TaskSubmission.Status reviewStatus, Long reviewerId,
                                             String reviewNote, Instant reviewedAt, Instant createdAt) {
        TaskSubmissionJpaEntity entity = new TaskSubmissionJpaEntity();
        entity.taskId = taskId;
        entity.authorId = authorId;
        entity.submissionNo = submissionNo;
        entity.content = content;
        entity.reviewStatus = reviewStatus;
        entity.reviewerId = reviewerId;
        entity.reviewNote = reviewNote;
        entity.reviewedAt = reviewedAt;
        entity.createdAt = createdAt;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public int getSubmissionNo() {
        return submissionNo;
    }

    public String getContent() {
        return content;
    }

    public TaskSubmission.Status getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(TaskSubmission.Status reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
