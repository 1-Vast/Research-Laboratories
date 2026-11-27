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
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;
import com.labflow.shared.audit.AuditRecorder;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 项目工作流用例：立项、审批、成员、结项、归档。
 *
 * <p>所有状态迁移都委托给 {@link Project} 聚合，应用服务只负责：
 * 取聚合 → 校验作用域与版本 → 调领域方法 → 落库 → 记审计 → 留审批轨迹。</p>
 */
@Service
public class ProjectService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "status", "plannedStart", "plannedEnd", "createdAt", "updatedAt", "labId");

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    private final LabScopeRepository labScope;
    private final UserDirectory users;
    private final ProjectScopeService scope;
    private final Clock clock;
    private final AuditRecorder audit;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, TaskRepository tasks,
                          LabScopeRepository labScope, UserDirectory users, ProjectScopeService scope,
                          Clock clock, AuditRecorder audit) {
        this.projects = projects;
        this.members = members;
        this.tasks = tasks;
        this.labScope = labScope;
        this.users = users;
        this.scope = scope;
        this.clock = clock;
        this.audit = audit;
    }

    /** 项目进度：已验收任务 / 非取消任务。 */
    public record Progress(long total, long done) {
        public int percent() {
            return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<Project> list(Long actorId, Long labId, ProjectStatus status, String keyword,
                                    int page, int size, String sort) {
        if (labId != null && !labScope.isMember(labId, actorId)) {
            throw new BusinessException(ErrorCode.LAB_SCOPE_FORBIDDEN);
        }
        SortSpec sortSpec = SortSpec.parse(sort, SORTABLE_FIELDS, "updatedAt");
        return projects.search(scope.piLabIds(actorId), actorId, labId, status, keyword, page, size, sortSpec);
    }

    @Transactional(readOnly = true)
    public Project get(Long id, Long actorId) {
        Project project = require(id);
        scope.assertCanRead(project, actorId);
        return project;
    }

    @Transactional(readOnly = true)
    public Progress progress(Long projectId) {
        return new Progress(tasks.countNotCanceled(projectId), tasks.countDone(projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> members(Long id, Long actorId) {
        Project project = require(id);
        scope.assertCanRead(project, actorId);
        return members.findMembers(project.id());
    }

    @Transactional(readOnly = true)
    public List<ProjectReview> reviews(Long id, Long actorId) {
        Project project = require(id);
        scope.assertCanRead(project, actorId);
        return members.findReviews(project.id());
    }

    @Transactional
    public Project create(CreateProjectCommand command, Long actorId) {
        if (!labScope.existsEnabled(command.labId())) {
            throw new BusinessException(ErrorCode.LAB_NOT_FOUND);
        }
        if (!labScope.isMember(command.labId(), actorId)) {
            throw new BusinessException(ErrorCode.LAB_SCOPE_FORBIDDEN, "只能在自己所属的实验室下创建项目");
        }
        Instant now = Instant.now(clock);
        Project saved = projects.save(Project.create(command.labId(), actorId, command.name(),
                command.objective(), command.plannedStart(), command.plannedEnd(), now));
        // 负责人同时写入项目成员表，后续任务分派与周报归属都以成员关系为准
        members.addMember(ProjectMember.of(saved.id(), actorId, ProjectMember.Type.LEADER, now));
        audit.record(actorId, "project.create", "project", saved.id(), AuditRecorder.RESULT_SUCCESS,
                "labId=" + saved.labId());
        return saved;
    }

    @Transactional
    public Project update(Long id, UpdateProjectCommand command, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        scope.assertVersion(project, command.version());
        project.updateBasicInfo(command.name(), command.objective(), command.plannedStart(),
                command.plannedEnd(), Instant.now(clock));
        Project saved = projects.save(project);
        audit.record(actorId, "project.update", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        project.assertDeletable();
        projects.delete(project);
        audit.record(actorId, "project.delete", "project", id, AuditRecorder.RESULT_SUCCESS, null);
    }

    @Transactional
    public Project submit(Long id, Integer version, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        scope.assertVersion(project, version);
        project.submit(Instant.now(clock));
        Project saved = projects.save(project);
        audit.record(actorId, "project.submit", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public Project approve(Long id, ReviewProjectCommand command, Long actorId) {
        Project project = require(id);
        scope.assertLabPi(project, actorId);
        scope.assertVersion(project, command.version());
        Instant now = Instant.now(clock);
        project.approve(actorId, now);
        Project saved = projects.save(project);
        members.addReview(ProjectReview.record(id, ProjectReview.Type.INIT, actorId,
                ReviewDecision.APPROVE, null, now));
        audit.record(actorId, "project.approve", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public Project reject(Long id, ReviewProjectCommand command, Long actorId) {
        Project project = require(id);
        scope.assertLabPi(project, actorId);
        scope.assertVersion(project, command.version());
        Instant now = Instant.now(clock);
        project.reject(actorId, command.reason(), now);
        Project saved = projects.save(project);
        members.addReview(ProjectReview.record(id, ProjectReview.Type.INIT, actorId,
                ReviewDecision.REJECT, command.reason(), now));
        audit.record(actorId, "project.reject", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    /** 被驳回的项目由负责人改回草稿，保留原有审批轨迹。 */
    @Transactional
    public Project reopen(Long id, Integer version, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        scope.assertVersion(project, version);
        project.reopen(Instant.now(clock));
        Project saved = projects.save(project);
        audit.record(actorId, "project.reopen", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public Project requestClose(Long id, CloseProjectCommand command, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        scope.assertVersion(project, command.version());
        project.assertActiveForTask();
        if (tasks.countOpen(id) > 0) {
            throw new BusinessException(ErrorCode.OPEN_TASKS_EXIST);
        }
        project.requestClose(command.closeNote(), Instant.now(clock));
        Project saved = projects.save(project);
        audit.record(actorId, "project.closeRequest", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public Project approveClose(Long id, ReviewProjectCommand command, Long actorId) {
        Project project = require(id);
        scope.assertLabPi(project, actorId);
        scope.assertVersion(project, command.version());
        Instant now = Instant.now(clock);
        project.approveClose(actorId, now);
        Project saved = projects.save(project);
        members.addReview(ProjectReview.record(id, ProjectReview.Type.CLOSE, actorId,
                ReviewDecision.APPROVE, null, now));
        audit.record(actorId, "project.closeApprove", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public Project rejectClose(Long id, ReviewProjectCommand command, Long actorId) {
        Project project = require(id);
        scope.assertLabPi(project, actorId);
        scope.assertVersion(project, command.version());
        Instant now = Instant.now(clock);
        project.rejectClose(actorId, command.reason(), now);
        Project saved = projects.save(project);
        members.addReview(ProjectReview.record(id, ProjectReview.Type.CLOSE, actorId,
                ReviewDecision.REJECT, command.reason(), now));
        audit.record(actorId, "project.closeReject", "project", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public ProjectMember addMember(Long id, Long userId, ProjectMember.Type type, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        project.assertMemberManageable();
        if (!users.existsEnabled(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (members.isActiveMember(id, userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是项目成员");
        }
        Instant now = Instant.now(clock);
        ProjectMember member = ProjectMember.of(id, userId,
                type == null ? ProjectMember.Type.MEMBER : type, now);
        members.addMember(member);
        audit.record(actorId, "project.member.add", "project", id, AuditRecorder.RESULT_SUCCESS,
                "userId=" + userId);
        return member;
    }

    @Transactional
    public void removeMember(Long id, Long userId, Long actorId) {
        Project project = require(id);
        scope.assertLeader(project, actorId);
        project.assertMemberManageable();
        if (project.isLeader(userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "不能移除项目负责人本人");
        }
        if (!members.isActiveMember(id, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该用户不是项目成员");
        }
        members.deactivateMember(id, userId);
        audit.record(actorId, "project.member.remove", "project", id, AuditRecorder.RESULT_SUCCESS,
                "userId=" + userId);
    }

    private Project require(Long id) {
        return projects.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
