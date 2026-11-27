package com.labflow.research.application;

import com.labflow.research.application.command.CloseProjectCommand;
import com.labflow.research.application.command.CreateProjectCommand;
import com.labflow.research.application.command.ReviewProjectCommand;
import com.labflow.research.application.command.UpdateProjectCommand;
import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectMember;
import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.research.domain.ReviewDecision;
import com.labflow.research.domain.repository.LabScopeRepository;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import com.labflow.research.domain.repository.ProjectRepository;
import com.labflow.research.domain.repository.TaskRepository;
import com.labflow.research.domain.repository.UserDirectory;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目用例测试：把「谁能做什么」的每一条分支都钉住。
 *
 * <p>这里用 Mockito 隔离数据库，只验证应用服务的编排逻辑：作用域校验顺序、
 * 版本号比对、审批轨迹写入、审计记录。领域规则本身由 ProjectTest 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final long LEADER_ID = 10L;
    private static final long PI_ID = 20L;
    private static final long MEMBER_ID = 30L;
    private static final long LAB_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectMemberRepository members;
    @Mock
    private TaskRepository tasks;
    @Mock
    private LabScopeRepository labScope;
    @Mock
    private UserDirectory users;
    @Mock
    private AuditRecorder audit;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        ProjectScopeService scope = new ProjectScopeService(members, labScope);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ProjectService(projects, members, tasks, labScope, users, scope, clock, audit);
    }

    private Project project(long id, ProjectStatus status) {
        return Project.restore(id, LAB_ID, LEADER_ID, "项目" + id, "目标", status,
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31), null, 1, NOW, NOW);
    }

    /** 只桩「查得到」，不桩保存：失败路径不应该触发写库，否则严格模式会报多余桩。 */
    private void givenExisting(Project project) {
        when(projects.findById(project.id())).thenReturn(Optional.of(project));
    }

    /** 成功路径额外桩保存，回写传入的聚合。 */
    private void givenSavable() {
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ErrorCode codeOf(Runnable action) {
        BusinessException ex = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(ex).isNotNull();
        return ex.code();
    }

    // ---------------------------------------------------------------- 创建

    @Test
    @DisplayName("实验室不存在时创建失败")
    void createFailsWhenLabMissing() {
        when(labScope.existsEnabled(LAB_ID)).thenReturn(false);
        ErrorCode code = codeOf(() -> service.create(new CreateProjectCommand(LAB_ID, "项目", "目标", null, null),
                LEADER_ID));
        assertThat(code).isEqualTo(ErrorCode.LAB_NOT_FOUND);
        verify(projects, never()).save(any());
    }

    @Test
    @DisplayName("不在该实验室时不能创建项目")
    void createFailsWhenNotLabMember() {
        when(labScope.existsEnabled(LAB_ID)).thenReturn(true);
        when(labScope.isMember(LAB_ID, LEADER_ID)).thenReturn(false);
        ErrorCode code = codeOf(() -> service.create(new CreateProjectCommand(LAB_ID, "项目", "目标", null, null),
                LEADER_ID));
        assertThat(code).isEqualTo(ErrorCode.LAB_SCOPE_FORBIDDEN);
    }

    @Test
    @DisplayName("创建成功时负责人同时写入成员表，并记审计")
    void createRegistersLeaderAsMember() {
        when(labScope.existsEnabled(LAB_ID)).thenReturn(true);
        when(labScope.isMember(LAB_ID, LEADER_ID)).thenReturn(true);
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project created = service.create(new CreateProjectCommand(LAB_ID, "新项目", "目标", null, null), LEADER_ID);

        assertThat(created.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(created.leaderId()).isEqualTo(LEADER_ID);

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(members).addMember(captor.capture());
        assertThat(captor.getValue().memberType()).isEqualTo(ProjectMember.Type.LEADER);
        assertThat(captor.getValue().userId()).isEqualTo(LEADER_ID);
        verify(audit).record(eq(LEADER_ID), eq("project.create"), eq("project"), any(), any(), any());
    }

    // ---------------------------------------------------------------- 查询

    @Test
    @DisplayName("按实验室过滤时，非本实验室成员直接拒绝")
    void listRejectsForeignLab() {
        when(labScope.isMember(LAB_ID, LEADER_ID)).thenReturn(false);
        ErrorCode code = codeOf(() -> service.list(LEADER_ID, LAB_ID, null, null, 1, 20, null));
        assertThat(code).isEqualTo(ErrorCode.LAB_SCOPE_FORBIDDEN);
    }

    @Test
    @DisplayName("列表查询把 PI 实验室列表作为作用域传入仓储")
    void listPassesPiScope() {
        when(labScope.isMember(LAB_ID, LEADER_ID)).thenReturn(true);
        when(labScope.findPiLabIds(LEADER_ID)).thenReturn(List.of(LAB_ID));
        when(projects.search(any(), eq(LEADER_ID), eq(LAB_ID), any(), any(), eq(1), eq(20), any()))
                .thenReturn(com.labflow.shared.api.PageResult.of(List.of(), 0, 1, 20));

        service.list(LEADER_ID, LAB_ID, ProjectStatus.ACTIVE, "感知", 1, 20, "updatedAt,desc");

        verify(projects).search(List.of(LAB_ID), LEADER_ID, LAB_ID, ProjectStatus.ACTIVE, "感知", 1, 20,
                new com.labflow.shared.api.SortSpec("updatedAt", false));
    }

    @Test
    @DisplayName("项目不存在返回 404 语义")
    void getFailsWhenMissing() {
        when(projects.findById(99L)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.get(99L, LEADER_ID))).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("非成员读取详情被拒")
    void getRejectsOutsider() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(members.isActiveMember(1L, MEMBER_ID)).thenReturn(false);
        when(labScope.isPi(LAB_ID, MEMBER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.get(1L, MEMBER_ID))).isEqualTo(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName("进度按非取消任务与已验收任务统计")
    void progressUsesTaskCounts() {
        when(tasks.countNotCanceled(1L)).thenReturn(4L);
        when(tasks.countDone(1L)).thenReturn(1L);

        ProjectService.Progress progress = service.progress(1L);

        assertThat(progress.total()).isEqualTo(4);
        assertThat(progress.done()).isEqualTo(1);
        assertThat(progress.percent()).isEqualTo(25);
    }

    @Test
    @DisplayName("没有任务时进度为 0，不出现除零")
    void progressHandlesEmptyProject() {
        when(tasks.countNotCanceled(1L)).thenReturn(0L);
        when(tasks.countDone(1L)).thenReturn(0L);
        assertThat(service.progress(1L).percent()).isZero();
    }

    // ---------------------------------------------------------------- 修改

    @Test
    @DisplayName("非负责人不能修改项目")
    void updateRejectsNonLeader() {
        givenExisting(project(1L, ProjectStatus.DRAFT));
        ErrorCode code = codeOf(() -> service.update(1L,
                new UpdateProjectCommand("改名", null, null, null, 1), MEMBER_ID));
        assertThat(code).isEqualTo(ErrorCode.PROJECT_LEADER_REQUIRED);
    }

    @Test
    @DisplayName("版本号不一致时拒绝写入，避免覆盖他人修改")
    void updateRejectsStaleVersion() {
        givenExisting(project(1L, ProjectStatus.DRAFT));
        ErrorCode code = codeOf(() -> service.update(1L,
                new UpdateProjectCommand("改名", null, null, null, 7), LEADER_ID));
        assertThat(code).isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    @DisplayName("负责人带正确版本号可以修改草稿")
    void updateSucceedsWithMatchingVersion() {
        givenExisting(project(1L, ProjectStatus.DRAFT));
        givenSavable();
        Project updated = service.update(1L, new UpdateProjectCommand("新名称", "新目标", null, null, 1), LEADER_ID);
        assertThat(updated.name()).isEqualTo("新名称");
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("非草稿项目不能删除")
    void deleteRejectsNonDraft() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        assertThat(codeOf(() -> service.delete(1L, LEADER_ID))).isEqualTo(ErrorCode.PROJECT_NOT_DELETABLE);
        verify(projects, never()).delete(any());
    }

    @Test
    @DisplayName("草稿项目可以删除")
    void deleteDraft() {
        givenExisting(project(1L, ProjectStatus.DRAFT));
        service.delete(1L, LEADER_ID);
        verify(projects).delete(any(Project.class));
    }

    // ---------------------------------------------------------------- 审批

    @Test
    @DisplayName("提交立项后进入待审批")
    void submitMovesToPending() {
        givenExisting(project(1L, ProjectStatus.DRAFT));
        givenSavable();
        Project submitted = service.submit(1L, 1, LEADER_ID);
        assertThat(submitted.status()).isEqualTo(ProjectStatus.PENDING);
    }

    @Test
    @DisplayName("非本实验室 PI 不能审批")
    void approveRejectsNonPi() {
        givenExisting(project(1L, ProjectStatus.PENDING));
        when(labScope.isPi(LAB_ID, LEADER_ID)).thenReturn(false);
        ErrorCode code = codeOf(() -> service.approve(1L,
                new ReviewProjectCommand(ReviewDecision.APPROVE, null, 1), LEADER_ID));
        assertThat(code).isEqualTo(ErrorCode.LAB_SCOPE_FORBIDDEN);
    }

    @Test
    @DisplayName("PI 审批通过后记录立项审批轨迹")
    void approveRecordsReview() {
        givenExisting(project(1L, ProjectStatus.PENDING));
        givenSavable();
        when(labScope.isPi(LAB_ID, PI_ID)).thenReturn(true);

        Project approved = service.approve(1L, new ReviewProjectCommand(ReviewDecision.APPROVE, null, 1), PI_ID);

        assertThat(approved.status()).isEqualTo(ProjectStatus.ACTIVE);
        ArgumentCaptor<ProjectReview> captor = ArgumentCaptor.forClass(ProjectReview.class);
        verify(members).addReview(captor.capture());
        assertThat(captor.getValue().reviewType()).isEqualTo(ProjectReview.Type.INIT);
        assertThat(captor.getValue().decision()).isEqualTo(ReviewDecision.APPROVE);
        assertThat(captor.getValue().reviewerId()).isEqualTo(PI_ID);
    }

    @Test
    @DisplayName("PI 驳回后项目进入已驳回，并记录理由")
    void rejectRecordsReason() {
        givenExisting(project(1L, ProjectStatus.PENDING));
        givenSavable();
        when(labScope.isPi(LAB_ID, PI_ID)).thenReturn(true);

        Project rejected = service.reject(1L,
                new ReviewProjectCommand(ReviewDecision.REJECT, "目标不聚焦", 1), PI_ID);

        assertThat(rejected.status()).isEqualTo(ProjectStatus.REJECTED);
        ArgumentCaptor<ProjectReview> captor = ArgumentCaptor.forClass(ProjectReview.class);
        verify(members).addReview(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("目标不聚焦");
    }

    @Test
    @DisplayName("驳回不填理由属于参数错误")
    void rejectRequiresReason() {
        givenExisting(project(1L, ProjectStatus.PENDING));
        when(labScope.isPi(LAB_ID, PI_ID)).thenReturn(true);
        ErrorCode code = codeOf(() -> service.reject(1L,
                new ReviewProjectCommand(ReviewDecision.REJECT, "  ", 1), PI_ID));
        assertThat(code).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("已驳回项目可退回草稿重新编辑")
    void reopenMovesBackToDraft() {
        givenExisting(project(1L, ProjectStatus.REJECTED));
        givenSavable();
        assertThat(service.reopen(1L, 1, LEADER_ID).status()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    @DisplayName("仍有未验收任务时不能申请结项")
    void requestCloseBlockedByOpenTasks() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(tasks.countOpen(1L)).thenReturn(2L);
        assertThat(codeOf(() -> service.requestClose(1L, new CloseProjectCommand("完成", 1), LEADER_ID)))
                .isEqualTo(ErrorCode.OPEN_TASKS_EXIST);
    }

    @Test
    @DisplayName("任务全部验收后可以申请结项")
    void requestCloseSucceeds() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        givenSavable();
        when(tasks.countOpen(1L)).thenReturn(0L);
        Project closing = service.requestClose(1L, new CloseProjectCommand("全部交付", 1), LEADER_ID);
        assertThat(closing.status()).isEqualTo(ProjectStatus.CLOSING);
        assertThat(closing.closeNote()).isEqualTo("全部交付");
    }

    @Test
    @DisplayName("PI 批准结项后项目归档")
    void approveCloseArchives() {
        givenExisting(project(1L, ProjectStatus.CLOSING));
        givenSavable();
        when(labScope.isPi(LAB_ID, PI_ID)).thenReturn(true);
        Project archived = service.approveClose(1L,
                new ReviewProjectCommand(ReviewDecision.APPROVE, null, 1), PI_ID);
        assertThat(archived.status()).isEqualTo(ProjectStatus.ARCHIVED);
        ArgumentCaptor<ProjectReview> captor = ArgumentCaptor.forClass(ProjectReview.class);
        verify(members).addReview(captor.capture());
        assertThat(captor.getValue().reviewType()).isEqualTo(ProjectReview.Type.CLOSE);
    }

    @Test
    @DisplayName("PI 退回结项后项目回到进行中")
    void rejectCloseReturnsToActive() {
        givenExisting(project(1L, ProjectStatus.CLOSING));
        givenSavable();
        when(labScope.isPi(LAB_ID, PI_ID)).thenReturn(true);
        Project active = service.rejectClose(1L,
                new ReviewProjectCommand(ReviewDecision.REJECT, "还有成果未登记", 1), PI_ID);
        assertThat(active.status()).isEqualTo(ProjectStatus.ACTIVE);
    }

    // ---------------------------------------------------------------- 成员

    @Test
    @DisplayName("添加不存在的用户返回 404 语义")
    void addMemberRejectsUnknownUser() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(users.existsEnabled(MEMBER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.addMember(1L, MEMBER_ID, null, LEADER_ID)))
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("重复添加成员返回 409")
    void addMemberRejectsDuplicate() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(users.existsEnabled(MEMBER_ID)).thenReturn(true);
        when(members.isActiveMember(1L, MEMBER_ID)).thenReturn(true);
        assertThat(codeOf(() -> service.addMember(1L, MEMBER_ID, null, LEADER_ID)))
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("添加成员默认类型为 MEMBER")
    void addMemberDefaultsToMember() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(users.existsEnabled(MEMBER_ID)).thenReturn(true);
        when(members.isActiveMember(1L, MEMBER_ID)).thenReturn(false);

        ProjectMember member = service.addMember(1L, MEMBER_ID, null, LEADER_ID);

        assertThat(member.memberType()).isEqualTo(ProjectMember.Type.MEMBER);
        verify(members).addMember(any(ProjectMember.class));
    }

    @Test
    @DisplayName("不能把负责人本人移出项目")
    void removeMemberRejectsLeader() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        assertThat(codeOf(() -> service.removeMember(1L, LEADER_ID, LEADER_ID)))
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("移除不存在的成员返回 404")
    void removeMemberRejectsNonMember() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(members.isActiveMember(1L, MEMBER_ID)).thenReturn(false);
        assertThat(codeOf(() -> service.removeMember(1L, MEMBER_ID, LEADER_ID)))
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("正常移除成员调用失活而非物理删除")
    void removeMemberDeactivates() {
        givenExisting(project(1L, ProjectStatus.ACTIVE));
        when(members.isActiveMember(1L, MEMBER_ID)).thenReturn(true);
        service.removeMember(1L, MEMBER_ID, LEADER_ID);
        verify(members).deactivateMember(1L, MEMBER_ID);
    }

    @Test
    @DisplayName("成员与审批轨迹查询都走作用域校验")
    void membersAndReviewsRequireScope() {
        Project active = project(1L, ProjectStatus.ACTIVE);
        when(projects.findById(1L)).thenReturn(Optional.of(active));
        when(members.findMembers(1L)).thenReturn(List.of(
                ProjectMember.of(1L, LEADER_ID, ProjectMember.Type.LEADER, NOW)));
        when(members.findReviews(1L)).thenReturn(List.of());

        assertThat(service.members(1L, LEADER_ID)).hasSize(1);
        assertThat(service.reviews(1L, LEADER_ID)).isEmpty();
    }
}
