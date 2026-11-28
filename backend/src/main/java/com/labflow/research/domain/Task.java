package com.labflow.research.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;

import java.time.Instant;

/**
 * 科研任务聚合根。
 *
 * <p>状态机：</p>
 * <pre>
 * TODO --start--> IN_PROGRESS --submit--> SUBMITTED --approve--> DONE
 *                      ^                        |
 *                      +-------return-----------+
 * TODO / IN_PROGRESS / RETURNED --cancel--> CANCELED
 * </pre>
 *
 * <p>关键不变量：只有受派人本人可以开始与提交；审核人不得是受派人本人（禁止自审）。</p>
 */
public class Task {

    private Long id;
    private Long projectId;
    private Long assigneeId;
    private String title;
    private String description;
    private TaskStatus status;
    private Priority priority;
    private Instant dueAt;
    private String cancelReason;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    private Task() {
    }

    public static Task create(Long projectId, Long assigneeId, String title, String description,
                              Priority priority, Instant dueAt, Instant now) {
        Task task = new Task();
        task.projectId = projectId;
        task.assigneeId = assigneeId;
        task.title = requireText(title, "任务标题不能为空");
        task.description = description;
        task.status = TaskStatus.TODO;
        task.priority = priority == null ? Priority.NORMAL : priority;
        task.dueAt = dueAt;
        task.version = 0;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public static Task restore(Long id, Long projectId, Long assigneeId, String title, String description,
                               TaskStatus status, Priority priority, Instant dueAt, String cancelReason,
                               int version, Instant createdAt, Instant updatedAt) {
        Task task = new Task();
        task.id = id;
        task.projectId = projectId;
        task.assigneeId = assigneeId;
        task.title = title;
        task.description = description;
        task.status = status;
        task.priority = priority;
        task.dueAt = dueAt;
        task.cancelReason = cancelReason;
        task.version = version;
        task.createdAt = createdAt;
        task.updatedAt = updatedAt;
        return task;
    }

    /** 只有未开始的任务允许修改标题、描述、优先级与截止时间。 */
    public void update(String title, String description, Priority priority, Instant dueAt, Instant now) {
        if (status != TaskStatus.TODO) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "只有未开始的任务可以修改");
        }
        this.title = requireText(title, "任务标题不能为空");
        this.description = description;
        this.priority = priority == null ? Priority.NORMAL : priority;
        this.dueAt = dueAt;
        touch(now);
    }

    /** TODO / RETURNED → IN_PROGRESS，只能由受派人本人操作。 */
    public void start(Long actorId, Instant now) {
        assertAssignee(actorId, "只有受派人本人可以开始任务");
        if (status != TaskStatus.TODO && status != TaskStatus.RETURNED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不允许开始任务（当前状态：" + status + "）");
        }
        this.status = TaskStatus.IN_PROGRESS;
        touch(now);
    }

    /** IN_PROGRESS → SUBMITTED，同时生成一条不可变交付快照。 */
    public TaskSubmission submit(Long actorId, String content, int submissionNo, Instant now) {
        assertAssignee(actorId, "只有受派人本人可以提交任务");
        if (status != TaskStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "只有进行中的任务可以提交（当前状态：" + status + "）");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "提交内容不能为空");
        }
        this.status = TaskStatus.SUBMITTED;
        touch(now);
        return TaskSubmission.submit(id, actorId, submissionNo, content.trim(), now);
    }

    /** SUBMITTED → DONE / RETURNED；审核人不得是受派人本人。 */
    public void review(Long actorId, ReviewDecision decision, String note, Instant now) {
        if (status != TaskStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "只有已提交的任务可以验收（当前状态：" + status + "）");
        }
        if (assigneeId.equals(actorId)) {
            throw new BusinessException(ErrorCode.SELF_REVIEW_FORBIDDEN, "不能验收自己提交的任务");
        }
        if (decision == ReviewDecision.REJECT && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "退回任务必须填写原因");
        }
        this.status = decision == ReviewDecision.APPROVE ? TaskStatus.DONE : TaskStatus.RETURNED;
        touch(now);
    }

    /** TODO / IN_PROGRESS / RETURNED → CANCELED，取消必须写明原因。 */
    public void cancel(String reason, Instant now) {
        if (status == TaskStatus.SUBMITTED || status == TaskStatus.DONE || status == TaskStatus.CANCELED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "已提交或已验收的任务不能取消");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "取消任务必须填写原因");
        }
        this.cancelReason = reason.trim();
        this.status = TaskStatus.CANCELED;
        touch(now);
    }

    public void assertDeletable() {
        if (status != TaskStatus.TODO) {
            throw new BusinessException(ErrorCode.TASK_NOT_DELETABLE);
        }
    }

    public boolean isAssignee(Long userId) {
        return assigneeId != null && assigneeId.equals(userId);
    }

    public boolean countsForProgress() {
        return status != TaskStatus.CANCELED;
    }

    private void assertAssignee(Long actorId, String message) {
        if (!isAssignee(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private void touch(Instant now) {
        this.version++;
        this.updatedAt = now;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }

    public Long id() {
        return id;
    }

    public Long projectId() {
        return projectId;
    }

    public Long assigneeId() {
        return assigneeId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public TaskStatus status() {
        return status;
    }

    public Priority priority() {
        return priority;
    }

    public Instant dueAt() {
        return dueAt;
    }

    public String cancelReason() {
        return cancelReason;
    }

    public int version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
