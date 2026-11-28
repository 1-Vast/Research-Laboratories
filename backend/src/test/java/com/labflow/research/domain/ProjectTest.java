package com.labflow.research.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 项目状态机单元测试：只测领域规则，不启动 Spring。
 * 覆盖任务书 §4.3 要求的“非法状态跳转必须被后端拒绝”。
 */
@DisplayName("项目状态机")
class ProjectTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-27T00:00:00Z");
    private static final Long LEADER = 10L;
    private static final Long PI = 20L;

    private Project draft() {
        return Project.create(1L, LEADER, "文献分类模型评估", "整理公开数据并评估模型",
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1), NOW);
    }

    @Test
    @DisplayName("新项目默认草稿，版本号为 0")
    void newProjectIsDraft() {
        Project project = draft();
        assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(project.version()).isZero();
        assertThat(project.leaderId()).isEqualTo(LEADER);
    }

    @Test
    @DisplayName("草稿 → 待审批 → 进行中：合法主路径，版本号递增")
    void happyPathToActive() {
        Project project = draft();
        project.submit(NOW);
        assertThat(project.status()).isEqualTo(ProjectStatus.PENDING);
        assertThat(project.version()).isEqualTo(1);

        project.approve(PI, LATER);
        assertThat(project.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("草稿直接审批被拒绝：非法状态跳转")
    void cannotApproveDraft() {
        Project project = draft();
        assertThatThrownBy(() -> project.approve(PI, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    @DisplayName("负责人审批自己的项目被拒绝：禁止自审")
    void leaderCannotApproveOwnProject() {
        Project project = draft();
        project.submit(NOW);
        assertThatThrownBy(() -> project.approve(LEADER, LATER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SELF_REVIEW_FORBIDDEN);
        assertThat(project.status()).isEqualTo(ProjectStatus.PENDING);
    }

    @Test
    @DisplayName("驳回必须填理由，驳回后可重新编辑回草稿")
    void rejectRequiresReasonAndCanReopen() {
        Project project = draft();
        project.submit(NOW);

        assertThatThrownBy(() -> project.reject(PI, "  ", LATER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        project.reject(PI, "计划过于笼统", LATER);
        assertThat(project.status()).isEqualTo(ProjectStatus.REJECTED);

        project.reopen(LATER);
        assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    @DisplayName("只有草稿或已驳回可以改基本信息")
    void onlyDraftOrRejectedEditable() {
        Project project = draft();
        project.submit(NOW);
        assertThatThrownBy(() -> project.updateBasicInfo("新名字", null, null, null, LATER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("计划结束日期早于开始日期被拒绝")
    void planEndBeforeStartRejected() {
        assertThatThrownBy(() -> Project.create(1L, LEADER, "项目", null,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 1), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("进行中 → 申请结项 → 归档")
    void closeAndArchive() {
        Project project = draft();
        project.submit(NOW);
        project.approve(PI, LATER);
        project.requestClose("全部任务已验收", LATER);
        assertThat(project.status()).isEqualTo(ProjectStatus.CLOSING);

        project.approveClose(PI, LATER);
        assertThat(project.status()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(project.isArchived()).isTrue();
    }

    @Test
    @DisplayName("草稿不能申请结项")
    void draftCannotRequestClose() {
        assertThatThrownBy(() -> draft().requestClose("x", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("退回结项回到进行中，并清空结项说明")
    void rejectCloseReturnsToActive() {
        Project project = draft();
        project.submit(NOW);
        project.approve(PI, LATER);
        project.requestClose("说明", LATER);

        project.rejectClose(PI, "还有任务没验收", LATER);
        assertThat(project.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.closeNote()).isNull();
    }

    @Test
    @DisplayName("只有草稿可以删除")
    void onlyDraftDeletable() {
        Project project = draft();
        project.assertDeletable();

        project.submit(NOW);
        assertThatThrownBy(project::assertDeletable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.PROJECT_NOT_DELETABLE);
    }

    @Test
    @DisplayName("成员维护与任务操作要求项目处于进行中")
    void memberAndTaskOperationsRequireActive() {
        Project project = draft();
        assertThatThrownBy(project::assertMemberManageable).isInstanceOf(BusinessException.class);
        assertThatThrownBy(project::assertActiveForTask).isInstanceOf(BusinessException.class);

        project.submit(NOW);
        project.approve(PI, LATER);
        project.assertMemberManageable();
        project.assertActiveForTask();
    }

    @Test
    @DisplayName("项目名称不能为空")
    void nameRequired() {
        assertThatThrownBy(() -> Project.create(1L, LEADER, "   ", null, null, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
