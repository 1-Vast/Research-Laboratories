package com.labflow.identity.interfaces.rest;

import com.labflow.identity.application.AuthService;
import com.labflow.identity.domain.User;
import com.labflow.identity.interfaces.rest.vo.MeVo;
import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.security.CurrentUser;
import com.labflow.shared.web.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户信息与权限：登录后前端据此渲染菜单；权限每次从数据库读取，变更后下一次请求即生效。 */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<MeVo> me() {
        User user = authService.currentUser(CurrentUser.requireUserId());
        return ApiResponse.ok(IdentityWebMapper.toMeVo(user), TraceContext.currentTraceId());
    }
}
