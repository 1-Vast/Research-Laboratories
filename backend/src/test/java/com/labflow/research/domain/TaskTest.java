package com.labflow.research.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务状态机单元测试。
 * 重点：只有受派人可以开始/提交；审核不得自审；退回后重新提交生成新的交付快照。
 */
@DisplayName("任务状态机")
class TaskTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private static final Long PROJECT = 100L;
    private static final Long ASSIGNEE = 31L;
    private static final Long LEADER = 30L;

    private Task task() {
        return Task.create(PROJECT, ASSIGNEE, "整理评估数据", "提交统计说明", Priority.HIGH, null, NOW);
    }

    @Test
    @DisplayName("新任务默认待办，优先级可空时回落 NORMAL")
    void newTaskIsTodo() {
        Task created = task();
        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(created.priority()).isEqualTo(Priority.HIGH);
        assertThat(created.version()).isZero();

        Task fallback = Task.create(PROJECT, ASSIGNEE, "标题", null, null, null, NOW);
        assertThat(fallback.priority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    @DisplayName("完整流转：待办 → 进行中 → 已提交 → 已验收")
    void fullHappyPath() {
        Task task = task();
        task.start(ASSIGNEE, NOW);
        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);

        TaskSubmission submission = task.submit(ASSIGNEE, "已整理 120 条样本", 1, NOW);
        assertThat(task.status()).isEqualTo(TaskStatus.SUBMITTED);
        assertThat(submission.submissionNo()).isEqualTo(1);
        assertThat(submission.reviewStatus()).isEqualTo(TaskSubmission.Status.SUBMITTED);

        task.review(LEADER, ReviewDecision.APPROVE, "符合要求", NOW);
        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("非受派人开始任务被拒绝")
    void otherUserCannotStart() {
        Task task = task();
        assertThatThrownBy(() -> task.start(LEADER, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("非受派人提交交付被拒绝")
    void otherUserCannotSubmit() {
        Task task = task();
        task.start(ASSIGNEE, NOW);
        assertThatThrownBy(() -> task.submit(LEADER, "内容", 1, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("从待办直接提交被拒绝：非法状态跳转")
    void cannotSubmitFromTodo() {
        Task task = task();
        assertThatThrownBy(() -> task.submit(ASSIGNEE, "内容", 1, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("提交内容为空被拒绝")
    void submitRequiresContent() {
        Task task = task();
        task.start(ASSIGNEE, NOW);
        assertThatThrownBy(() -> task.submit(ASSIGNEE, "  ", 1, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("验收自己提交的任务被拒绝：禁止自审")
    void cannotSelfReview() {
        Task task = task();
        task.start(ASSIGNEE, NOW);
        task.submit(ASSIGNEE, "内容", 1, NOW);

        assertThatThrownBy(() -> task.review(ASSIGNEE, ReviewDecision.APPROVE, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SELF_REVIEW_FORBIDDEN);
        assertThat(task.status()).isEqualTo(TaskStatus.SUBMITTED);
    }

    @Test
    @DisplayName("退回必须填原因；退回后可重新开始并再次提交")
    void returnRequiresReasonThenCanResubmit() {
        Task task = task();
        task.start(ASSIGNEE, NOW);
        task.submit(ASSIGNEE, "第一版", 1, NOW);

        assertThatThrownBy(() -> task.review(LEADER, ReviewDecision.REJECT, " ", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        task.review(LEADER, ReviewDecision.REJECT, "缺少结果说明", NOW);
        assertThat(task.status()).isEqualTo(TaskStatus.RETURNED);

        task.start(ASSIGNEE, NOW);
        TaskSubmission second = task.submit(ASSIGNEE, "第二版，补充结果说明", 2, NOW);
        assertThat(second.submissionNo()).isEqualTo(2);
        assertThat(task.status()).isEqualTo(TaskStatus.SUBMITTED);
    }

    @Test
    @DisplayName("未提交的任务不能验收")
    void cannotReviewUnsubmitted() {
        Task task = task();
        task.start(ASSIGNEE, NOW);
        assertThatThrownBy(() -> task.review(LEADER, ReviewDecision.APPROVE, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("取消必须填原因；已提交或已验收不可取消")
    void cancelRules() {
        Task task = task();
        assertThatThrownBy(() -> task.cancel("", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        task.cancel("需求变更", NOW);
        assertThat(task.status()).isEqualTo(TaskStatus.CANCELED);
        assertThat(task.cancelReason()).isEqualTo("需求变更");
        assertThat(task.countsForProgress()).isFalse();

        Task submitted = task();
        submitted.start(ASSIGNEE, NOW);
        submitted.submit(ASSIGNEE, "内容", 1, NOW);
        assertThatThrownBy(() -> submitted.cancel("想取消", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("只有未开始的任务可以修改与删除")
    void editAndDeleteOnlyWhenTodo() {
        Task task = task();
        task.update("新标题", "新描述", Priority.LOW, null, NOW);
        assertThat(task.title()).isEqualTo("新标题");
        assertThat(task.version()).isEqualTo(1);
        task.assertDeletable();

        task.start(ASSIGNEE, NOW);
        assertThatThrownBy(() -> task.update("再改", null, null, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        assertThatThrownBy(task::assertDeletable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.TASK_NOT_DELETABLE);
    }

    @Test
    @DisplayName("非取消任务计入进度分母")
    void canceledExcludedFromProgress() {
        assertThat(task().countsForProgress()).isTrue();
        Task canceled = task();
        canceled.cancel("取消", NOW);
        assertThat(canceled.countsForProgress()).isFalse();
    }
}
