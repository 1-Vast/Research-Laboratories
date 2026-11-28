package com.labflow.research.application;

import com.labflow.research.application.command.CreateTaskCommand;
import com.labflow.research.application.command.ReviewTaskCommand;
import com.labflow.research.application.command.SubmitTaskCommand;
import com.labflow.research.application.command.UpdateTaskCommand;
import com.labflow.research.domain.Project;
import com.labflow.research.domain.ReviewDecision;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskStatus;
import com.labflow.research.domain.TaskSubmission;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import com.labflow.research.domain.repository.ProjectRepository;
import com.labflow.research.domain.repository.TaskRepository;
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
 * 任务工作流用例：分派、开始、提交、验收、退回、取消。
 *
 * <p>关键点：所有按 {@code taskId} 访问的接口，都必须先由任务反查所属项目，
 * 再用项目成员关系校验归属。只校验 URL 上的 projectId 是可以被绕过的
 * （改 taskId 就能读到别人项目的任务）。</p>
 */
@Service
public class TaskService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "status", "priority", "dueAt", "createdAt", "updatedAt");

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectScopeService scope;
    private final Clock clock;
    private final AuditRecorder audit;

    public TaskService(TaskRepository tasks, ProjectRepository projects, ProjectMemberRepository members,
                       ProjectScopeService scope, Clock clock, AuditRecorder audit) {
        this.tasks = tasks;
        this.projects = projects;
        this.members = members;
        this.scope = scope;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<Task> list(Long projectId, TaskStatus status, Long assigneeId, String keyword,
                                 int page, int size, String sort, Long actorId) {
        Project project = requireProject(projectId);
        scope.assertCanRead(project, actorId);
        SortSpec sortSpec = SortSpec.parse(sort, SORTABLE_FIELDS, "createdAt");
        return tasks.search(projectId, status, assigneeId, keyword, page, size, sortSpec);
    }

    /** 「我的任务」：只返回指派给自己的任务，天然带归属，不需要额外作用域校验。 */
    @Transactional(readOnly = true)
    public PageResult<Task> mine(Long actorId, TaskStatus status, int page, int size, String sort) {
        SortSpec sortSpec = SortSpec.parse(sort, SORTABLE_FIELDS, "dueAt");
        return tasks.search(null, status, actorId, null, page, size, sortSpec);
    }

    @Transactional(readOnly = true)
    public Task get(Long taskId, Long actorId) {
        Task task = requireTask(taskId);
        scope.assertCanRead(requireProject(task.projectId()), actorId);
        return task;
    }

    @Transactional(readOnly = true)
    public List<TaskSubmission> submissions(Long taskId, Long actorId) {
        Task task = requireTask(taskId);
        scope.assertCanRead(requireProject(task.projectId()), actorId);
        return tasks.findSubmissions(taskId);
    }

    @Transactional
    public Task create(Long projectId, CreateTaskCommand command, Long actorId) {
        Project project = requireProject(projectId);
        scope.assertLeader(project, actorId);
        project.assertActiveForTask();
        if (!members.isActiveMember(projectId, command.assigneeId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "受派人必须是该项目成员");
        }
        Task saved = tasks.save(Task.create(projectId, command.assigneeId(), command.title(),
                command.description(), command.priority(), command.dueAt(), Instant.now(clock)));
        audit.record(actorId, "task.create", "task", saved.id(), AuditRecorder.RESULT_SUCCESS,
                "projectId=" + projectId + ",assigneeId=" + command.assigneeId());
        return saved;
    }

    @Transactional
    public Task update(Long taskId, UpdateTaskCommand command, Long actorId) {
        Task task = loadManageable(taskId, actorId);
        assertVersion(task, command.version());
        task.update(command.title(), command.description(), command.priority(), command.dueAt(),
                Instant.now(clock));
        Task saved = tasks.save(task);
        audit.record(actorId, "task.update", "task", taskId, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public void delete(Long taskId, Long actorId) {
        Task task = loadManageable(taskId, actorId);
        task.assertDeletable();
        tasks.delete(task);
        audit.record(actorId, "task.delete", "task", taskId, AuditRecorder.RESULT_SUCCESS, null);
    }

    /** 开始任务：只有受派人本人可以操作，领域层再校验一次状态。 */
    @Transactional
    public Task start(Long taskId, Integer version, Long actorId) {
        Task task = requireTask(taskId);
        Project project = requireProject(task.projectId());
        scope.assertCanRead(project, actorId);
        project.assertActiveForTask();
        assertVersion(task, version);
        task.start(actorId, Instant.now(clock));
        Task saved = tasks.save(task);
        audit.record(actorId, "task.start", "task", taskId, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public Task submit(Long taskId, SubmitTaskCommand command, Long actorId) {
        Task task = requireTask(taskId);
        Project project = requireProject(task.projectId());
        scope.assertCanRead(project, actorId);
        project.assertActiveForTask();
        assertVersion(task, command.version());
        Instant now = Instant.now(clock);
        int submissionNo = tasks.nextSubmissionNo(taskId);
        TaskSubmission submission = task.submit(actorId, command.content(), submissionNo, now);
        Task saved = tasks.save(task);
        tasks.saveSubmission(submission);
        audit.record(actorId, "task.submit", "task", taskId, AuditRecorder.RESULT_SUCCESS,
                "submissionNo=" + submissionNo);
        return saved;
    }

    /** 验收：负责人执行，领域层禁止自审；同时把最近一次交付快照标记为已审核。 */
    @Transactional
    public Task review(Long taskId, ReviewTaskCommand command, Long actorId) {
        Task task = requireTask(taskId);
        Project project = requireProject(task.projectId());
        scope.assertLeader(project, actorId);
        assertVersion(task, command.version());
        Instant now = Instant.now(clock);
        task.review(actorId, command.decision(), command.note(), now);
        Task saved = tasks.save(task);
        markLatestSubmissionReviewed(taskId, command, actorId, now);
        audit.record(actorId, "task.review", "task", taskId, AuditRecorder.RESULT_SUCCESS,
                "decision=" + command.decision());
        return saved;
    }

    @Transactional
    public Task cancel(Long taskId, String reason, Integer version, Long actorId) {
        Task task = loadManageable(taskId, actorId);
        assertVersion(task, version);
        task.cancel(reason, Instant.now(clock));
        Task saved = tasks.save(task);
        audit.record(actorId, "task.cancel", "task", taskId, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    private void markLatestSubmissionReviewed(Long taskId, ReviewTaskCommand command, Long actorId, Instant now) {
        List<TaskSubmission> submissions = tasks.findSubmissions(taskId);
        for (int i = submissions.size() - 1; i >= 0; i--) {
            TaskSubmission submission = submissions.get(i);
            if (submission.reviewStatus() == TaskSubmission.Status.SUBMITTED) {
                submission.markReviewed(command.decision() == ReviewDecision.APPROVE, actorId,
                        command.note(), now);
                tasks.saveSubmission(submission);
                return;
            }
        }
    }

    /** 修改 / 删除 / 取消：必须是项目负责人。 */
    private Task loadManageable(Long taskId, Long actorId) {
        Task task = requireTask(taskId);
        scope.assertLeader(requireProject(task.projectId()), actorId);
        return task;
    }

    private void assertVersion(Task task, Integer expected) {
        if (expected != null && expected != task.version()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                    "任务已被他人修改（当前版本 " + task.version() + "），请刷新后重试");
        }
    }

    private Task requireTask(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

    private Project requireProject(Long projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
