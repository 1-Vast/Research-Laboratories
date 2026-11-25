package com.labflow.identity.interfaces.rest;

import com.labflow.identity.application.RoleAdminService;
import com.labflow.identity.application.command.CreateRoleCommand;
import com.labflow.identity.application.command.UpdateRoleCommand;
import com.labflow.identity.domain.Role;
import com.labflow.identity.interfaces.rest.dto.AssignPermissionsRequest;
import com.labflow.identity.interfaces.rest.dto.CreateRoleRequest;
import com.labflow.identity.interfaces.rest.dto.UpdateRoleRequest;
import com.labflow.identity.interfaces.rest.vo.PermissionVo;
import com.labflow.identity.interfaces.rest.vo.RoleVo;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色与权限管理接口，后端强制校验 role:manage。 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
@PreAuthorize("hasAuthority('role:manage')")
public class AdminRoleController {

    private final RoleAdminService roleAdminService;

    public AdminRoleController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/roles")
    public ApiResponse<PageResult<RoleVo>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        PageResult<Role> result = roleAdminService.list(keyword, page, size, sort);
        return ApiResponse.ok(result.map(IdentityWebMapper::toRoleVo), TraceContext.currentTraceId());
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<RoleVo> detail(@PathVariable Long id) {
        return ApiResponse.ok(IdentityWebMapper.toRoleVo(roleAdminService.get(id)),
                TraceContext.currentTraceId());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleVo> create(@Valid @RequestBody CreateRoleRequest request) {
        Role saved = roleAdminService.create(new CreateRoleCommand(request.code(), request.name(),
                request.description()), CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toRoleVo(saved), TraceContext.currentTraceId());
    }

    @PatchMapping("/roles/{id}")
    public ApiResponse<RoleVo> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        Role saved = roleAdminService.update(id, new UpdateRoleCommand(request.name(), request.description()),
                CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toRoleVo(saved), TraceContext.currentTraceId());
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        roleAdminService.delete(id, CurrentUser.requireUserId());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionVo>> permissions() {
        return ApiResponse.ok(roleAdminService.listPermissions().stream()
                .map(IdentityWebMapper::toPermissionVo).toList(), TraceContext.currentTraceId());
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<RoleVo> assignPermissions(@PathVariable Long id,
                                                 @Valid @RequestBody AssignPermissionsRequest request) {
        Role saved = roleAdminService.assignPermissions(id, request.permissions(), CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toRoleVo(saved), TraceContext.currentTraceId());
    }
}
