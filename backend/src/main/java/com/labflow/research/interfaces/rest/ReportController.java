package com.labflow.research.interfaces.rest;

import com.labflow.research.application.ReportService;
import com.labflow.research.application.command.CreateReportCommand;
import com.labflow.research.application.command.ReviewReportCommand;
import com.labflow.research.application.command.UpdateReportCommand;
import com.labflow.research.domain.Report;
import com.labflow.research.domain.ReportStatus;
import com.labflow.research.interfaces.rest.dto.CreateReportRequest;
import com.labflow.research.interfaces.rest.dto.ReviewReportRequest;
import com.labflow.research.interfaces.rest.dto.UpdateReportRequest;
import com.labflow.research.interfaces.rest.dto.VersionRequest;
import com.labflow.research.interfaces.rest.vo.ReportVo;
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
 * 周报工作流接口。
 *
 * <p>列表不返回关联任务（避免每行一次查询），详情返回完整 taskIds；
 * 需要任务明细时再调 `/tasks/{id}`。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/projects/{projectId}/reports")
    @PreAuthorize("hasAuthority('report:read')")
    public ApiResponse<PageResult<ReportVo>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        PageResult<Report> result = reportService.list(projectId, authorId, status, page, size, sort,
                CurrentUser.requireUserId());
        return ApiResponse.ok(result.map(report -> ResearchWebMapper.toReportVo(report, List.of())),
                TraceContext.currentTraceId());
    }

    @PostMapping("/projects/{projectId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('report:create')")
    public ApiResponse<ReportVo> create(@PathVariable Long projectId,
                                        @Valid @RequestBody CreateReportRequest request) {
        Report saved = reportService.create(projectId, new CreateReportCommand(request.weekStart(),
                request.work(), request.blockers(), request.nextPlan(), request.taskIds()),
                CurrentUser.requireUserId());
        return ApiResponse.ok(ResearchWebMapper.toReportVo(saved, request.taskIds()),
                TraceContext.currentTraceId());
    }

    @GetMapping("/reports/{id}")
    @PreAuthorize("hasAuthority('report:read')")
    public ApiResponse<ReportVo> detail(@PathVariable Long id) {
        Long actorId = CurrentUser.requireUserId();
        Report report = reportService.get(id, actorId);
        return ApiResponse.ok(ResearchWebMapper.toReportVo(report, reportService.taskIds(id, actorId)),
                TraceContext.currentTraceId());
    }

    @PutMapping("/reports/{id}")
    @PreAuthorize("hasAuthority('report:update')")
    public ApiResponse<ReportVo> update(@PathVariable Long id, @Valid @RequestBody UpdateReportRequest request) {
        Long actorId = CurrentUser.requireUserId();
        Report saved = reportService.update(id, new UpdateReportCommand(request.work(), request.blockers(),
                request.nextPlan(), request.taskIds(), request.version()), actorId);
        return ApiResponse.ok(ResearchWebMapper.toReportVo(saved, reportService.taskIds(id, actorId)),
                TraceContext.currentTraceId());
    }

    @DeleteMapping("/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('report:delete')")
    public void delete(@PathVariable Long id) {
        reportService.delete(id, CurrentUser.requireUserId());
    }

    @PostMapping("/reports/{id}/submit")
    @PreAuthorize("hasAuthority('report:submit')")
    public ApiResponse<ReportVo> submit(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        Long actorId = CurrentUser.requireUserId();
        Report saved = reportService.submit(id, request.version(), actorId);
        return ApiResponse.ok(ResearchWebMapper.toReportVo(saved, reportService.taskIds(id, actorId)),
                TraceContext.currentTraceId());
    }

    @PostMapping("/reports/{id}/review")
    @PreAuthorize("hasAuthority('report:review')")
    public ApiResponse<ReportVo> review(@PathVariable Long id, @Valid @RequestBody ReviewReportRequest request) {
        Long actorId = CurrentUser.requireUserId();
        Report saved = reportService.review(id, new ReviewReportCommand(request.decision(), request.note(),
                request.version()), actorId);
        return ApiResponse.ok(ResearchWebMapper.toReportVo(saved, reportService.taskIds(id, actorId)),
                TraceContext.currentTraceId());
    }
}
