package com.labflow.research.application;

import com.labflow.research.application.command.CreateTaskCommand;
import com.labflow.research.application.command.ReviewTaskCommand;
import com.labflow.research.application.command.SubmitTaskCommand;
import com.labflow.research.application.command.UpdateTaskCommand;
import com.labflow.research.domain.Priority;
import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.research.domain.ReviewDecision;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskStatus;
import com.labflow.research.domain.TaskSubmission;
import com.labflow.research.domain.repository.LabScopeRepository;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import com.labflow.research.domain.repository.ProjectRepository;
import com.labflow.research.domain.repository.TaskRepository;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务用例测试：钉住受派人边界、项目状态约束与交付快照的审核回写。 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final long PROJECT_ID = 5L;
    private static final long TASK_ID = 50L;
    private static final long LEADER_ID = 10L;
    private static final long ASSIGNEE_ID = 30L;
    private static final long OUTSIDER_ID = 99L;
    private static final long LAB_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    @Mock
    private TaskRepository tasks;
    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectMemberRepository members;
    @Mock
    private LabScopeRepository labScope;
    @Mock
    private AuditRecorder audit;

    private TaskService service;

    @BeforeEach
    void setUp() {
        ProjectScopeService scope = new ProjectScopeService(members, labScope);
        service = new TaskService(tasks, projects, members, scope, Clock.fixed(NOW, ZoneOffset.UTC), audit);
    }

    private Project project(ProjectStatus status) {
        return Project.restore(PROJECT_ID, LAB_ID, LEADER_ID, "项目", "目标", status,
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31), null, 1, NOW, NOW);
    }

    private Task task(TaskStatus status, int version) {
        return Task.restore(TASK_ID, PROJECT_ID, ASSIGNEE_ID, "任务", "描述", status, Priority.NORMAL,
                null, null, version, NOW, NOW);
    }

    /** 只桩「查得到」：失败路径不写库，桩了保存会触发严格模式告警。 */
    private void givenTask(Task task) {
        when(tasks.findById(TASK_ID)).thenReturn(Optional.of(task));
    }

    /** 成功路径额外桩保存。 */
    private void givenSavable() {
        when(tasks.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ErrorCode codeOf(Runnable action) {
        BusinessException ex = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(ex).isNotNull();
        return ex.code();
    }

    @Test
    @DisplayName("项目不存在时列表直接 404")
    void listFailsWhenProjectMissing() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.list(PROJECT_ID, null, null, null, 1, 20, null, LEADER_ID)))
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("非成员不能查看项目任务列表")
    void listRejectsOutsider() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        when(labScope.isPi(LAB_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.list(PROJECT_ID, null, null, null, 1, 20, null, OUTSIDER_ID)))
                .isEqualTo(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName("项目成员可以按条件检索任务")
    void listReturnsForMember() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(tasks.search(eq(PROJECT_ID), eq(TaskStatus.TODO), any(), any(), eq(1), eq(20), any()))
                .thenReturn(com.labflow.shared.api.PageResult.of(List.of(), 0, 1, 20));

        service.list(PROJECT_ID, TaskStatus.TODO, null, "标定", 1, 20, "priority,desc", LEADER_ID);

        verify(tasks).search(eq(PROJECT_ID), eq(TaskStatus.TODO), eq(null), eq("标定"), eq(1), eq(20),
                eq(new com.labflow.shared.api.SortSpec("priority", false)));
    }

    @Test
    @DisplayName("我的任务只按受派人过滤")
    void mineFiltersByAssignee() {
        when(tasks.search(eq(null), eq(null), eq(ASSIGNEE_ID), eq(null), eq(1), eq(20), any()))
                .thenReturn(com.labflow.shared.api.PageResult.of(List.of(), 0, 1, 20));
        service.mine(ASSIGNEE_ID, null, 1, 20, null);
        verify(tasks).search(eq(null), eq(null), eq(ASSIGNEE_ID), eq(null), eq(1), eq(20), any());
    }

    @Test
    @DisplayName("任务不存在返回 404")
    void getFailsWhenTaskMissing() {
        when(tasks.findById(TASK_ID)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.get(TASK_ID, LEADER_ID))).isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    @DisplayName("任务所属项目被删除后按项目不存在处理")
    void getFailsWhenProjectMissing() {
        when(tasks.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.TODO, 0)));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.get(TASK_ID, LEADER_ID))).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("非成员不能查看任务详情")
    void getRejectsOutsider() {
        when(tasks.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.TODO, 0)));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        when(labScope.isPi(LAB_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.get(TASK_ID, OUTSIDER_ID)))
                .isEqualTo(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName("非负责人不能创建任务")
    void createRejectsNonLeader() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateTaskCommand(ASSIGNEE_ID, "任务", null, null, null), ASSIGNEE_ID)))
                .isEqualTo(ErrorCode.PROJECT_LEADER_REQUIRED);
    }

    @Test
    @DisplayName("草稿项目不能创建任务")
    void createRejectsDraftProject() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.DRAFT)));
        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateTaskCommand(ASSIGNEE_ID, "任务", null, null, null), LEADER_ID)))
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("受派人必须是项目成员")
    void createRejectsNonMemberAssignee() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.create(PROJECT_ID,
                new CreateTaskCommand(OUTSIDER_ID, "任务", null, null, null), LEADER_ID)))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("创建任务默认优先级为 NORMAL，并记审计")
    void createDefaultsPriority() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, ASSIGNEE_ID)).thenReturn(true);
        when(tasks.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task created = service.create(PROJECT_ID,
                new CreateTaskCommand(ASSIGNEE_ID, "任务", "描述", null, null), LEADER_ID);

        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(created.priority()).isEqualTo(Priority.NORMAL);
        verify(audit).record(eq(LEADER_ID), eq("task.create"), eq("task"), any(), any(), any());
    }

    @Test
    @DisplayName("修改任务时版本号不一致返回 409")
    void updateRejectsStaleVersion() {
        givenTask(task(TaskStatus.TODO, 2));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        assertThat(codeOf(() -> service.update(TASK_ID,
                new UpdateTaskCommand("改名", null, null, null, 1), LEADER_ID)))
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    @DisplayName("进行中的任务不能改标题")
    void updateRejectsStartedTask() {
        givenTask(task(TaskStatus.IN_PROGRESS, 1));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        assertThat(codeOf(() -> service.update(TASK_ID,
                new UpdateTaskCommand("改名", null, null, null, 1), LEADER_ID)))
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("非未开始任务不能删除")
    void deleteRejectsNonTodo() {
        givenTask(task(TaskStatus.IN_PROGRESS, 1));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        assertThat(codeOf(() -> service.delete(TASK_ID, LEADER_ID))).isEqualTo(ErrorCode.TASK_NOT_DELETABLE);
        verify(tasks, never()).delete(any());
    }

    @Test
    @DisplayName("非受派人不能开始任务")
    void startRejectsNonAssignee() {
        givenTask(task(TaskStatus.TODO, 0));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        assertThat(codeOf(() -> service.start(TASK_ID, 0, LEADER_ID))).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("受派人开始任务后进入进行中")
    void startSucceedsForAssignee() {
        givenTask(task(TaskStatus.TODO, 0));
        givenSavable();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, ASSIGNEE_ID)).thenReturn(true);

        Task started = service.start(TASK_ID, 0, ASSIGNEE_ID);

        assertThat(started.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(started.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("提交任务写入递增的交付快照")
    void submitCreatesNumberedSnapshot() {
        givenTask(task(TaskStatus.IN_PROGRESS, 1));
        givenSavable();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, ASSIGNEE_ID)).thenReturn(true);
        when(tasks.nextSubmissionNo(TASK_ID)).thenReturn(3);

        service.submit(TASK_ID, new SubmitTaskCommand("交付内容", 1), ASSIGNEE_ID);

        ArgumentCaptor<TaskSubmission> captor = ArgumentCaptor.forClass(TaskSubmission.class);
        verify(tasks).saveSubmission(captor.capture());
        assertThat(captor.getValue().submissionNo()).isEqualTo(3);
        assertThat(captor.getValue().reviewStatus()).isEqualTo(TaskSubmission.Status.SUBMITTED);
        verify(audit).record(eq(ASSIGNEE_ID), eq("task.submit"), eq("task"), eq(TASK_ID), any(),
                eq("submissionNo=3"));
    }

    @Test
    @DisplayName("验收由负责人执行，并把最近一次交付标记为通过")
    void reviewMarksLatestSubmission() {
        givenTask(task(TaskStatus.SUBMITTED, 2));
        givenSavable();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        TaskSubmission latest = TaskSubmission.restore(9L, TASK_ID, ASSIGNEE_ID, 1, "交付",
                TaskSubmission.Status.SUBMITTED, null, null, null, NOW);
        when(tasks.findSubmissions(TASK_ID)).thenReturn(List.of(latest));

        Task reviewed = service.review(TASK_ID,
                new ReviewTaskCommand(ReviewDecision.APPROVE, "通过", 2), LEADER_ID);

        assertThat(reviewed.status()).isEqualTo(TaskStatus.DONE);
        assertThat(latest.reviewStatus()).isEqualTo(TaskSubmission.Status.APPROVED);
        assertThat(latest.reviewerId()).isEqualTo(LEADER_ID);
        verify(tasks).saveSubmission(latest);
    }

    @Test
    @DisplayName("受派人不能验收自己的任务")
    void reviewRejectsSelf() {
        givenTask(task(TaskStatus.SUBMITTED, 2));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        // 受派人不是负责人，先被作用域拦下；此处验证不会误判为可自审
        assertThat(codeOf(() -> service.review(TASK_ID,
                new ReviewTaskCommand(ReviewDecision.APPROVE, null, 2), ASSIGNEE_ID)))
                .isEqualTo(ErrorCode.PROJECT_LEADER_REQUIRED);
    }

    @Test
    @DisplayName("取消任务写入原因并计入审计")
    void cancelWritesReason() {
        givenTask(task(TaskStatus.TODO, 0));
        givenSavable();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));

        Task canceled = service.cancel(TASK_ID, "需求变更", 0, LEADER_ID);

        assertThat(canceled.status()).isEqualTo(TaskStatus.CANCELED);
        assertThat(canceled.cancelReason()).isEqualTo("需求变更");
    }

    @Test
    @DisplayName("交付快照查询同样走项目作用域")
    void submissionsRequireScope() {
        when(tasks.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.SUBMITTED, 2)));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(tasks.findSubmissions(TASK_ID)).thenReturn(List.of());

        assertThat(service.submissions(TASK_ID, LEADER_ID)).isEmpty();
        verify(tasks).findSubmissions(TASK_ID);
    }

    @Test
    @DisplayName("提交空内容属于参数错误")
    void submitRejectsBlankContent() {
        givenTask(task(TaskStatus.IN_PROGRESS, 1));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(members.isActiveMember(PROJECT_ID, ASSIGNEE_ID)).thenReturn(true);
        when(tasks.nextSubmissionNo(anyLong())).thenReturn(1);

        assertThat(codeOf(() -> service.submit(TASK_ID, new SubmitTaskCommand("  ", 1), ASSIGNEE_ID)))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(tasks, never()).saveSubmission(any());
    }

    @Test
    @DisplayName("任务列表排序字段白名单外直接拒绝")
    void listRejectsUnknownSortField() {
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        assertThat(codeOf(() -> service.list(PROJECT_ID, null, null, null, 1, 20, "assigneeId", LEADER_ID)))
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
