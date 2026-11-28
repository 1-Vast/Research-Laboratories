package com.labflow.research.interfaces.rest;

import com.labflow.research.application.ProjectScopeService;
import com.labflow.research.application.ProjectService;
import com.labflow.research.application.command.CloseProjectCommand;
import com.labflow.research.application.command.CreateProjectCommand;
import com.labflow.research.application.command.ReviewProjectCommand;
import com.labflow.research.application.command.UpdateProjectCommand;
import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectMember;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.research.interfaces.rest.dto.AddProjectMemberRequest;
import com.labflow.research.interfaces.rest.dto.CloseProjectRequest;
import com.labflow.research.interfaces.rest.dto.CreateProjectRequest;
import com.labflow.research.interfaces.rest.dto.ReviewProjectRequest;
import com.labflow.research.interfaces.rest.dto.UpdateProjectRequest;
import com.labflow.research.interfaces.rest.dto.VersionRequest;
import com.labflow.research.interfaces.rest.vo.ProjectDetailVo;
import com.labflow.research.interfaces.rest.vo.ProjectMemberVo;
import com.labflow.research.interfaces.rest.vo.ProjectReviewVo;
import com.labflow.research.interfaces.rest.vo.ProjectVo;
import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.security.CurrentUser;
import com.labflow.shared.web.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目工作流接口。
 *
 * <p>权限分两层：本类的方法用 {@code @PreAuthorize} 校验「有没有这个能力」，
 * 应用服务再用项目成员 / 实验室 PI 关系校验「在不在这个范围」。
 * 只靠前端隐藏按钮无法阻止直接调接口，因此两层都必须有。</p>
 */
@RestController
@RequestMapping("/api/v1/projects")
@Validated
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectScopeService scopeService;

    public ProjectController(ProjectService projectService, ProjectScopeService scopeService) {
        this.projectService = projectService;
        this.scopeService = scopeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<PageResult<ProjectVo>> list(
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        PageResult<Project> result = projectService.list(CurrentUser.requireUserId(), labId, status, keyword,
                page, size, sort);
        return ApiResponse.ok(result.map(ResearchWebMapper::toProjectVo), TraceContext.currentTraceId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<ProjectDetailVo> detail(@PathVariable Long id) {
        Long actorId = CurrentUser.requireUserId();
        Project project = projectService.get(id, actorId);
        ProjectService.Progress progress = projectService.progress(id);
        List<ProjectMemberVo> members = projectService.members(id, actorId).stream()
                .map(ResearchWebMapper::toMemberVo).toList();
        return ApiResponse.ok(new ProjectDetailVo(
                ResearchWebMapper.toProjectVo(project),
                ResearchWebMapper.toProgressVo(progress.total(), progress.done()),
                members), TraceContext.currentTraceId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:create')")
    public ApiResponse<ProjectVo> create(@Valid @RequestBody CreateProjectRequest request) {
        Project saved = projectService.create(new CreateProjectCommand(request.labId(), request.name(),
                request.objective(), request.plannedStart(), request.plannedEnd()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public ApiResponse<ProjectVo> update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        Project saved = projectService.update(id, new UpdateProjectCommand(request.name(), request.objective(),
                request.plannedStart(), request.plannedEnd(), request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:delete')")
    public void delete(@PathVariable Long id) {
        projectService.delete(id, CurrentUser.requireUserId());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('project:submit')")
    public ApiResponse<ProjectVo> submit(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        Project saved = projectService.submit(id, request.version(), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('project:approve')")
    public ApiResponse<ProjectVo> approve(@PathVariable Long id, @Valid @RequestBody ReviewProjectRequest request) {
        Project saved = projectService.approve(id, new ReviewProjectCommand(request.decision(), request.reason(),
                request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('project:approve')")
    public ApiResponse<ProjectVo> reject(@PathVariable Long id, @Valid @RequestBody ReviewProjectRequest request) {
        Project saved = projectService.reject(id, new ReviewProjectCommand(request.decision(), request.reason(),
                request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('project:update')")
    public ApiResponse<ProjectVo> reopen(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        Project saved = projectService.reopen(id, request.version(), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/close-request")
    @PreAuthorize("hasAuthority('project:close-request')")
    public ApiResponse<ProjectVo> requestClose(@PathVariable Long id,
                                               @Valid @RequestBody CloseProjectRequest request) {
        Project saved = projectService.requestClose(id, new CloseProjectCommand(request.closeNote(),
                request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/close-approve")
    @PreAuthorize("hasAuthority('project:close-review')")
    public ApiResponse<ProjectVo> approveClose(@PathVariable Long id,
                                               @Valid @RequestBody ReviewProjectRequest request) {
        Project saved = projectService.approveClose(id, new ReviewProjectCommand(request.decision(),
                request.reason(), request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/close-reject")
    @PreAuthorize("hasAuthority('project:close-review')")
    public ApiResponse<ProjectVo> rejectClose(@PathVariable Long id,
                                              @Valid @RequestBody ReviewProjectRequest request) {
        Project saved = projectService.rejectClose(id, new ReviewProjectCommand(request.decision(),
                request.reason(), request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toProjectVo(saved), TraceContext.currentTraceId());
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectMemberVo>> members(@PathVariable Long id) {
        List<ProjectMemberVo> members = projectService.members(id, CurrentUser.requireUserId()).stream()
                .map(ResearchWebMapper::toMemberVo).toList();
        return ApiResponse.ok(members, TraceContext.currentTraceId());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:member:manage')")
    public ApiResponse<ProjectMemberVo> addMember(@PathVariable Long id,
                                                  @Valid @RequestBody AddProjectMemberRequest request) {
        ProjectMember member = projectService.addMember(id, request.userId(), request.memberType(),
                CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toMemberVo(member), TraceContext.currentTraceId());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:member:manage')")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId, CurrentUser.requireUserId());
    }

    @GetMapping("/{id}/reviews")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectReviewVo>> reviews(@PathVariable Long id) {
        List<ProjectReviewVo> reviews = projectService.reviews(id, CurrentUser.requireUserId()).stream()
                .map(ResearchWebMapper::toReviewVo).toList();
        return ApiResponse.ok(reviews, TraceContext.currentTraceId());
    }

    /** 供前端判断「当前用户能否审批该项目」，避免前端自己推断 PI 关系。 */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<ProjectPermissionVo> permissions(@PathVariable Long id) {
        Long actorId = CurrentUser.requireUserId();
        Project project = projectService.get(id, actorId);
        return ApiResponse.ok(new ProjectPermissionVo(project.isLeader(actorId),
                scopeService.piLabIds(actorId).contains(project.labId())),
                TraceContext.currentTraceId());
    }

    /** 当前用户在该项目上的能力标记。 */
    public record ProjectPermissionVo(boolean leader, boolean labPi) {
    }
}
