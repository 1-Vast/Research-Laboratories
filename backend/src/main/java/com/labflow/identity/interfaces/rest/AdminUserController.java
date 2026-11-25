package com.labflow.identity.interfaces.rest;

import com.labflow.identity.application.UserAdminService;
import com.labflow.identity.application.command.CreateUserCommand;
import com.labflow.identity.application.command.UpdateUserCommand;
import com.labflow.identity.domain.User;
import com.labflow.identity.interfaces.rest.dto.AssignRolesRequest;
import com.labflow.identity.interfaces.rest.dto.CreateUserRequest;
import com.labflow.identity.interfaces.rest.dto.SetEnabledRequest;
import com.labflow.identity.interfaces.rest.dto.UpdateUserRequest;
import com.labflow.identity.interfaces.rest.vo.UserVo;
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

/**
 * 用户管理接口。后端强制校验 user:manage —— 即使绕过前端直接调接口，无权限同样 403。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
@PreAuthorize("hasAuthority('user:manage')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ApiResponse<PageResult<UserVo>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1")
            @Max(value = 100, message = "size 最大为 100") int size) {
        PageResult<User> result = userAdminService.list(keyword, page, size, sort);
        return ApiResponse.ok(result.map(IdentityWebMapper::toUserVo), TraceContext.currentTraceId());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserVo> detail(@PathVariable Long id) {
        return ApiResponse.ok(IdentityWebMapper.toUserVo(userAdminService.get(id)),
                TraceContext.currentTraceId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserVo> create(@Valid @RequestBody CreateUserRequest request) {
        User saved = userAdminService.create(new CreateUserCommand(request.username(), request.password(),
                request.displayName(), request.email(), request.roles()), CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toUserVo(saved), TraceContext.currentTraceId());
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserVo> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User saved = userAdminService.update(id, new UpdateUserCommand(request.displayName(), request.email()),
                CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toUserVo(saved), TraceContext.currentTraceId());
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<UserVo> setEnabled(@PathVariable Long id, @Valid @RequestBody SetEnabledRequest request) {
        User saved = userAdminService.setEnabled(id, request.enabled(), CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toUserVo(saved), TraceContext.currentTraceId());
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserVo> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        User saved = userAdminService.assignRoles(id, request.roles(), CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toUserVo(saved), TraceContext.currentTraceId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userAdminService.delete(id, CurrentUser.requireUserId());
    }
}
