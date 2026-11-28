package com.labflow.research.application;

import com.labflow.research.application.command.CreateReportCommand;
import com.labflow.research.application.command.ReviewReportCommand;
import com.labflow.research.application.command.UpdateReportCommand;
import com.labflow.research.domain.Project;
import com.labflow.research.domain.Report;
import com.labflow.research.domain.ReportStatus;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.repository.LabScopeRepository;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import com.labflow.research.domain.repository.ProjectRepository;
import com.labflow.research.domain.repository.ReportRepository;
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
import java.util.Objects;
import java.util.Set;

/**
 * 周报工作流用例：编写、提交、审核、退回重提。
 *
 * <p>三条不变量：同项目 + 同作者 + 同一周唯一；提交后正文只读；作者不能审核自己的周报。
 * 唯一性既有服务层预检（给出可读提示），也有数据库 `uq_reports_week` 兜底（防并发）。</p>
 */
@Service
public class ReportService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "weekStart", "status", "createdAt", "updatedAt");

    private final ReportRepository reports;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    private final LabScopeRepository labScope;
    private final Clock clock;
    private final AuditRecorder audit;

    public ReportService(ReportRepository reports, ProjectRepository projects,
                         ProjectMemberRepository members, TaskRepository tasks,
                         LabScopeRepository labScope, Clock clock, AuditRecorder audit) {
        this.reports = reports;
        this.projects = projects;
        this.members = members;
        this.tasks = tasks;
        this.labScope = labScope;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<Report> list(Long projectId, Long authorId, ReportStatus status, int page, int size,
                                   String sort, Long actorId) {
        Project project = requireProject(projectId);
        assertCanRead(project, actorId);
        SortSpec sortSpec = SortSpec.parse(sort, SORTABLE_FIELDS, "weekStart");
        return reports.search(projectId, authorId, status, page, size, sortSpec);
    }

    @Transactional(readOnly = true)
    public Report get(Long id, Long actorId) {
        Report report = require(id);
        assertCanRead(requireProject(report.projectId()), actorId);
        return report;
    }

    @Transactional(readOnly = true)
    public List<Long> taskIds(Long id, Long actorId) {
        Report report = get(id, actorId);
        return reports.findTaskIds(report.id());
    }

    @Transactional
    public Report create(Long projectId, CreateReportCommand command, Long actorId) {
        Project project = requireProject(projectId);
        if (!members.isActiveMember(projectId, actorId)) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_REQUIRED, "只有项目成员可以提交周报");
        }
        Instant now = Instant.now(clock);
        Report draft = Report.create(projectId, actorId, command.weekStart(), command.work(),
                command.blockers(), command.nextPlan(), now);
        if (reports.existsWeek(projectId, actorId, draft.weekStart())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT_WEEK);
        }
        Report saved = reports.save(draft);
        linkTasks(projectId, saved.id(), command.taskIds());
        audit.record(actorId, "report.create", "report", saved.id(), AuditRecorder.RESULT_SUCCESS,
                "projectId=" + projectId);
        return saved;
    }

    @Transactional
    public Report update(Long id, UpdateReportCommand command, Long actorId) {
        Report report = require(id);
        assertAuthor(report, actorId);
        assertVersion(report, command.version());
        report.update(command.work(), command.blockers(), command.nextPlan(), Instant.now(clock));
        Report saved = reports.save(report);
        linkTasks(report.projectId(), id, command.taskIds());
        audit.record(actorId, "report.update", "report", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        Report report = require(id);
        assertAuthor(report, actorId);
        report.assertDeletable();
        reports.delete(report);
        audit.record(actorId, "report.delete", "report", id, AuditRecorder.RESULT_SUCCESS, null);
    }

    @Transactional
    public Report submit(Long id, Integer version, Long actorId) {
        Report report = require(id);
        assertAuthor(report, actorId);
        assertVersion(report, version);
        report.submit(Instant.now(clock));
        Report saved = reports.save(report);
        audit.record(actorId, "report.submit", "report", id, AuditRecorder.RESULT_SUCCESS, null);
        return saved;
    }

    /** 审核：项目负责人或实验室 PI 可执行，作者本人被领域层拒绝。 */
    @Transactional
    public Report review(Long id, ReviewReportCommand command, Long actorId) {
        Report report = require(id);
        Project project = requireProject(report.projectId());
        if (!project.isLeader(actorId) && !labScope.isPi(project.labId(), actorId)) {
            throw new BusinessException(ErrorCode.PROJECT_LEADER_REQUIRED, "只有项目负责人或实验室 PI 可以审核周报");
        }
        assertVersion(report, command.version());
        report.review(actorId, command.decision(), command.note(), Instant.now(clock));
        Report saved = reports.save(report);
        audit.record(actorId, "report.review", "report", id, AuditRecorder.RESULT_SUCCESS,
                "decision=" + command.decision());
        return saved;
    }

    /** 关联任务必须属于同一项目，否则周报会混入别的项目进度。 */
    private void linkTasks(Long projectId, Long reportId, List<Long> taskIds) {
        if (taskIds == null) {
            return;
        }
        List<Long> distinct = taskIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long taskId : distinct) {
            Task task = tasks.findById(taskId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "关联任务不存在：" + taskId));
            if (!projectId.equals(task.projectId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只能关联同一项目下的任务");
            }
        }
        reports.linkTasks(reportId, distinct);
    }

    private void assertCanRead(Project project, Long actorId) {
        if (project.isLeader(actorId)
                || members.isActiveMember(project.id(), actorId)
                || labScope.isPi(project.labId(), actorId)) {
            return;
        }
        throw new BusinessException(ErrorCode.PROJECT_MEMBER_REQUIRED);
    }

    private void assertAuthor(Report report, Long actorId) {
        if (!report.isAuthor(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有作者本人可以操作该周报");
        }
    }

    private void assertVersion(Report report, Integer expected) {
        if (expected != null && expected != report.version()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                    "周报已被他人修改（当前版本 " + report.version() + "），请刷新后重试");
        }
    }

    private Report require(Long id) {
        return reports.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    private Project requireProject(Long projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
