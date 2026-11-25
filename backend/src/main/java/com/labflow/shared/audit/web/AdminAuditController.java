package com.labflow.shared.audit.web;

import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.audit.AuditEventView;
import com.labflow.shared.audit.AuditQueryService;
import com.labflow.shared.security.CurrentUser;
import com.labflow.shared.web.TraceContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询接口（audit:read）。
 * <p>平台管理员可查看全部脱敏记录；其他持有 audit:read 的角色（如 PI）只能看到与本人相关的记录，
 * 避免通过审计接口横向读取其他用户的操作轨迹。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Validated
@PreAuthorize("hasAuthority('audit:read')")
public class AdminAuditController {

    private final AuditQueryService auditQueryService;

    public AdminAuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditEventView>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        var principal = CurrentUser.require();
        boolean platformScope = principal.hasAuthority("user:manage");
        PageResult<AuditEventView> result = auditQueryService.search(action, actorId, page, size,
                platformScope, principal.userId());
        return ApiResponse.ok(result, TraceContext.currentTraceId());
    }
}
