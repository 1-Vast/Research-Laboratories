package com.labflow.research.interfaces.rest;

import com.labflow.research.application.TaskService;
import com.labflow.research.application.command.CreateTaskCommand;
import com.labflow.research.application.command.ReviewTaskCommand;
import com.labflow.research.application.command.SubmitTaskCommand;
import com.labflow.research.application.command.UpdateTaskCommand;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskStatus;
import com.labflow.research.interfaces.rest.dto.CancelTaskRequest;
import com.labflow.research.interfaces.rest.dto.CreateTaskRequest;
import com.labflow.research.interfaces.rest.dto.ReviewTaskRequest;
import com.labflow.research.interfaces.rest.dto.SubmitTaskRequest;
import com.labflow.research.interfaces.rest.dto.UpdateTaskRequest;
import com.labflow.research.interfaces.rest.dto.VersionRequest;
import com.labflow.research.interfaces.rest.vo.TaskSubmissionVo;
import com.labflow.research.interfaces.rest.vo.TaskVo;
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
 * 任务工作流接口。
 *
 * <p>按 taskId 访问的每个端点都会先由任务反查项目再校验成员关系，
 * 避免「改 URL 里的 taskId 就能操作别人项目任务」的水平越权。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks/mine")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<PageResult<TaskVo>> mine(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        PageResult<Task> result = taskService.mine(CurrentUser.requireUserId(), status, page, size, sort);
        return ApiResponse.ok(result.map(ResearchWebMapper::toTaskVo), TraceContext.currentTraceId());
    }

    @GetMapping("/projects/{projectId}/tasks")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<PageResult<TaskVo>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        PageResult<Task> result = taskService.list(projectId, status, assigneeId, keyword, page, size, sort,
                CurrentUser.requireUserId());
        return ApiResponse.ok(result.map(ResearchWebMapper::toTaskVo), TraceContext.currentTraceId());
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task:create')")
    public ApiResponse<TaskVo> create(@PathVariable Long projectId,
                                      @Valid @RequestBody CreateTaskRequest request) {
        Task saved = taskService.create(projectId, new CreateTaskCommand(request.assigneeId(), request.title(),
                request.description(), request.priority(), request.dueAt()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(saved), TraceContext.currentTraceId());
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<TaskVo> detail(@PathVariable Long id) {
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(taskService.get(id, CurrentUser.requireUserId())),
                TraceContext.currentTraceId());
    }

    @PutMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResponse<TaskVo> update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        Task saved = taskService.update(id, new UpdateTaskCommand(request.title(), request.description(),
                request.priority(), request.dueAt(), request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(saved), TraceContext.currentTraceId());
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task:delete')")
    public void delete(@PathVariable Long id) {
        taskService.delete(id, CurrentUser.requireUserId());
    }

    @PostMapping("/tasks/{id}/start")
    @PreAuthorize("hasAuthority('task:start')")
    public ApiResponse<TaskVo> start(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(
                taskService.start(id, request.version(), CurrentUser.requireUserId())),
                TraceContext.currentTraceId());
    }

    @PostMapping("/tasks/{id}/submit")
    @PreAuthorize("hasAuthority('task:submit')")
    public ApiResponse<TaskVo> submit(@PathVariable Long id, @Valid @RequestBody SubmitTaskRequest request) {
        Task saved = taskService.submit(id, new SubmitTaskCommand(request.content(), request.version()),
                CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/tasks/{id}/review")
    @PreAuthorize("hasAuthority('task:review')")
    public ApiResponse<TaskVo> review(@PathVariable Long id, @Valid @RequestBody ReviewTaskRequest request) {
        Task saved = taskService.review(id, new ReviewTaskCommand(request.decision(), request.note(),
                request.version()), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(saved), TraceContext.currentTraceId());
    }

    @PostMapping("/tasks/{id}/cancel")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResponse<TaskVo> cancel(@PathVariable Long id, @Valid @RequestBody CancelTaskRequest request) {
        Task saved = taskService.cancel(id, request.reason(), request.version(), CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toTaskVo(saved), TraceContext.currentTraceId());
    }

    @GetMapping("/tasks/{id}/submissions")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<List<TaskSubmissionVo>> submissions(@PathVariable Long id) {
        List<TaskSubmissionVo> submissions = taskService.submissions(id, CurrentUser.requireUserId()).stream()
                .map(ResearchWebMapper::toSubmissionVo).toList();
        return ApiResponse.ok(submissions, TraceContext.currentTraceId());
    }
}
