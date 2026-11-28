package com.labflow.research.domain;

import java.time.Instant;

/**
 * 任务交付快照。每次提交生成一条新记录，退回后重新提交不覆盖历史，
 * 保证答辩时可以展示“第 1 次提交被打回、第 2 次通过”的完整轨迹。
 */
public class TaskSubmission {

    /** 交付审核状态。 */
    public enum Status {
        SUBMITTED,
        APPROVED,
        RETURNED
    }

    private final Long id;
    private final Long taskId;
    private final Long authorId;
    private final int submissionNo;
    private final String content;
    private Status reviewStatus;
    private Long reviewerId;
    private String reviewNote;
    private Instant reviewedAt;
    private final Instant createdAt;

    private TaskSubmission(Long id, Long taskId, Long authorId, int submissionNo, String content,
                           Status reviewStatus, Long reviewerId, String reviewNote, Instant reviewedAt,
                           Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.authorId = authorId;
        this.submissionNo = submissionNo;
        this.content = content;
        this.reviewStatus = reviewStatus;
        this.reviewerId = reviewerId;
        this.reviewNote = reviewNote;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
    }

    public static TaskSubmission submit(Long taskId, Long authorId, int submissionNo, String content,
                                        Instant now) {
        return new TaskSubmission(null, taskId, authorId, submissionNo, content, Status.SUBMITTED,
                null, null, null, now);
    }

    public static TaskSubmission restore(Long id, Long taskId, Long authorId, int submissionNo, String content,
                                         Status reviewStatus, Long reviewerId, String reviewNote,
                                         Instant reviewedAt, Instant createdAt) {
        return new TaskSubmission(id, taskId, authorId, submissionNo, content, reviewStatus, reviewerId,
                reviewNote, reviewedAt, createdAt);
    }

    public void markReviewed(boolean approved, Long reviewerId, String note, Instant now) {
        this.reviewStatus = approved ? Status.APPROVED : Status.RETURNED;
        this.reviewerId = reviewerId;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long taskId() {
        return taskId;
    }

    public Long authorId() {
        return authorId;
    }

    public int submissionNo() {
        return submissionNo;
    }

    public String content() {
        return content;
    }

    public Status reviewStatus() {
        return reviewStatus;
    }

    public Long reviewerId() {
        return reviewerId;
    }

    public String reviewNote() {
        return reviewNote;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
