package com.labflow.research.application;

import com.labflow.research.application.command.CreateReportCommand;
import com.labflow.research.application.command.ReviewReportCommand;
import com.labflow.research.application.command.UpdateReportCommand;
import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.research.domain.Report;
import com.labflow.research.domain.ReportStatus;
import com.labflow.research.domain.ReviewDecision;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskStatus;
import com.labflow.research.domain.repository.LabScopeRepository;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import com.labflow.research.domain.repository.ProjectRepository;
import com.labflow.research.domain.repository.ReportRepository;
import com.labflow.research.domain.repository.TaskRepository;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 周报用例测试：唯一周、只读、作者边界与跨项目任务关联。 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final long PROJECT_ID = 5L;
    private static final long REPORT_ID = 60L;
    private static final long LEADER_ID = 10L;
    private static final long AUTHOR_ID = 30L;
    private static final long OUTSIDER_ID = 99L;
    private static final long LAB_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 23);

    @Mock
    private ReportRepository reports;
    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectMemberRepository members;
    @Mock
    private TaskRepository tasks;
    @Mock
    private LabScopeRepository labScope;
    @Mock
    private AuditRecorder audit;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reports, projects, members, tasks, labScope,
                Clock.fixed(NOW, ZoneOffset.UTC), audit);
    }

    private Project project() {
        return Project.restore(PROJECT_ID, LAB_ID, LEADER_ID, "项目", "目标", ProjectStatus.ACTIVE,
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31), null, 1, NOW, NOW);
    }

    private Report report(ReportStatus status, int version) {
        return Report.restore(REPORT_ID, PROJECT_ID, AUTHOR_ID, WEEK, "本周工作", null, null, status,
                null, null, version, NOW, NOW);
    }

    private void givenReport(Report report) {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));
    }

    private void givenSavable() {
        when(reports.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ErrorCode codeOf(Runnable action) {
        BusinessException ex = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(ex).isNotNull();
        return ex.code();
    }

    @Test
    @DisplayName("项目不存在时列表 404")
    void listFailsWhenProjectMissing() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.list(PROJECT_ID, null, null, 1, 20, null, LEADER_ID)))
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("非成员不能查看项目周报列表")
    void listRejectsOutsider() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        when(labScope.isPi(LAB_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.list(PROJECT_ID, null, null, 1, 20, null, OUTSIDER_ID)))
                .isEqualTo(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName("成员可按状态检索周报")
    void listReturnsForMember() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        when(reports.search(eq(PROJECT_ID), eq(AUTHOR_ID), eq(ReportStatus.DRAFT), eq(1), eq(20), any()))
                .thenReturn(com.labflow.shared.api.PageResult.of(List.of(), 0, 1, 20));

        service.list(PROJECT_ID, AUTHOR_ID, ReportStatus.DRAFT, 1, 20, "weekStart,desc", AUTHOR_ID);

        verify(reports).search(PROJECT_ID, AUTHOR_ID, ReportStatus.DRAFT, 1, 20,
                new com.labflow.shared.api.SortSpec("weekStart", false));
    }

    @Test
    @DisplayName("周报不存在返回 404")
    void getFailsWhenMissing() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.get(REPORT_ID, AUTHOR_ID))).isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("非成员不能读周报详情")
    void getRejectsOutsider() {
        givenReport(report(ReportStatus.DRAFT, 0));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        when(labScope.isPi(LAB_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.get(REPORT_ID, OUTSIDER_ID)))
                .isEqualTo(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName("关联任务列表读取同样走作用域")
    void taskIdsRequireScope() {
        givenReport(report(ReportStatus.DRAFT, 0));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        when(reports.findTaskIds(REPORT_ID)).thenReturn(List.of(7L));

        assertThat(service.taskIds(REPORT_ID, AUTHOR_ID)).containsExactly(7L);
    }

    @Test
    @DisplayName("非项目成员不能写周报")
    void createRejectsNonMember() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateReportCommand(WEEK, "工作", null, null, null), OUTSIDER_ID)))
                .isEqualTo(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName("同一周重复创建返回 409")
    void createRejectsDuplicateWeek() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        when(reports.existsWeek(PROJECT_ID, AUTHOR_ID, WEEK)).thenReturn(true);
        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateReportCommand(LocalDate.of(2026, 11, 25), "工作", null, null, null), AUTHOR_ID)))
                .isEqualTo(ErrorCode.DUPLICATE_REPORT_WEEK);
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("创建周报时周起始日归一，并记录审计")
    void createNormalizesWeek() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        when(reports.existsWeek(PROJECT_ID, AUTHOR_ID, WEEK)).thenReturn(false);
        givenSavable();

        Report created = service.create(PROJECT_ID,
                new CreateReportCommand(LocalDate.of(2026, 11, 26), "工作", "阻塞", "计划", null), AUTHOR_ID);

        assertThat(created.weekStart()).isEqualTo(WEEK);
        assertThat(created.status()).isEqualTo(ReportStatus.DRAFT);
        verify(audit).record(eq(AUTHOR_ID), eq("report.create"), eq("report"), any(), any(), any());
    }

    @Test
    @DisplayName("关联不存在的任务返回 404")
    void linkRejectsUnknownTask() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        when(reports.existsWeek(PROJECT_ID, AUTHOR_ID, WEEK)).thenReturn(false);
        givenSavable();
        when(tasks.findById(7L)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateReportCommand(WEEK, "工作", null, null, List.of(7L)), AUTHOR_ID)))
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    @DisplayName("不能关联其他项目的任务")
    void linkRejectsForeignTask() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        when(reports.existsWeek(PROJECT_ID, AUTHOR_ID, WEEK)).thenReturn(false);
        givenSavable();
        when(tasks.findById(7L)).thenReturn(Optional.of(Task.restore(7L, 999L, AUTHOR_ID, "别人的任务", null,
                TaskStatus.TODO, com.labflow.research.domain.Priority.NORMAL, null, null, 0, NOW, NOW)));

        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateReportCommand(WEEK, "工作", null, null, List.of(7L)), AUTHOR_ID)))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("非作者不能修改周报")
    void updateRejectsNonAuthor() {
        givenReport(report(ReportStatus.DRAFT, 0));
        assertThat(codeOf(() -> service.update(REPORT_ID,
                new UpdateReportCommand("改", null, null, null, 0), LEADER_ID)))
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("版本号不一致拒绝修改")
    void updateRejectsStaleVersion() {
        givenReport(report(ReportStatus.DRAFT, 3));
        assertThat(codeOf(() -> service.update(REPORT_ID,
                new UpdateReportCommand("改", null, null, null, 1), AUTHOR_ID)))
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    @DisplayName("作者可以修改草稿")
    void updateSucceedsForAuthor() {
        givenReport(report(ReportStatus.DRAFT, 0));
        givenSavable();

        Report updated = service.update(REPORT_ID,
                new UpdateReportCommand("新内容", "阻塞", "计划", null, 0), AUTHOR_ID);

        assertThat(updated.work()).isEqualTo("新内容");
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("非草稿周报不能删除")
    void deleteRejectsNonDraft() {
        givenReport(report(ReportStatus.SUBMITTED, 1));
        assertThat(codeOf(() -> service.delete(REPORT_ID, AUTHOR_ID)))
                .isEqualTo(ErrorCode.REPORT_NOT_DELETABLE);
        verify(reports, never()).delete(any());
    }

    @Test
    @DisplayName("作者删除草稿周报")
    void deleteDraft() {
        givenReport(report(ReportStatus.DRAFT, 0));
        service.delete(REPORT_ID, AUTHOR_ID);
        verify(reports).delete(any(Report.class));
    }

    @Test
    @DisplayName("提交周报后进入待审核")
    void submitMovesToSubmitted() {
        givenReport(report(ReportStatus.DRAFT, 0));
        givenSavable();
        assertThat(service.submit(REPORT_ID, 0, AUTHOR_ID).status()).isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    @DisplayName("既非负责人也非 PI 不能审核")
    void reviewRejectsOutsider() {
        givenReport(report(ReportStatus.SUBMITTED, 1));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(labScope.isPi(LAB_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.review(REPORT_ID,
                new ReviewReportCommand(ReviewDecision.APPROVE, null, 1), OUTSIDER_ID)))
                .isEqualTo(ErrorCode.PROJECT_LEADER_REQUIRED);
    }

    @Test
    @DisplayName("负责人审核通过，记录审核人与意见")
    void reviewSucceedsForLeader() {
        givenReport(report(ReportStatus.SUBMITTED, 1));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        givenSavable();

        Report reviewed = service.review(REPORT_ID,
                new ReviewReportCommand(ReviewDecision.APPROVE, "记录清晰", 1), LEADER_ID);

        assertThat(reviewed.status()).isEqualTo(ReportStatus.APPROVED);
        assertThat(reviewed.reviewerId()).isEqualTo(LEADER_ID);
        assertThat(reviewed.reviewNote()).isEqualTo("记录清晰");
    }

    @Test
    @DisplayName("作者同时是负责人时也不能自审，由领域层拦截")
    void reviewRejectsSelfReview() {
        Report own = Report.restore(REPORT_ID, PROJECT_ID, LEADER_ID, WEEK, "工作", null, null,
                ReportStatus.SUBMITTED, null, null, 1, NOW, NOW);
        givenReport(own);
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));

        assertThat(codeOf(() -> service.review(REPORT_ID,
                new ReviewReportCommand(ReviewDecision.APPROVE, null, 1), LEADER_ID)))
                .isEqualTo(ErrorCode.SELF_REVIEW_FORBIDDEN);
    }

    @Test
    @DisplayName("退回必须写意见")
    void reviewRequiresNoteOnReturn() {
        givenReport(report(ReportStatus.SUBMITTED, 1));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        assertThat(codeOf(() -> service.review(REPORT_ID,
                new ReviewReportCommand(ReviewDecision.REJECT, "  ", 1), LEADER_ID)))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("排序字段白名单外直接拒绝")
    void listRejectsUnknownSortField() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(members.isActiveMember(PROJECT_ID, AUTHOR_ID)).thenReturn(true);
        assertThat(codeOf(() -> service.list(PROJECT_ID, null, null, 1, 20, "authorId", AUTHOR_ID)))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
