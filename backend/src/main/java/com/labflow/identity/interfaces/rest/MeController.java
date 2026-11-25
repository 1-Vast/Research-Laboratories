package com.labflow.identity.interfaces.rest;

import com.labflow.identity.application.AuthService;
import com.labflow.identity.domain.User;
import com.labflow.identity.interfaces.rest.dto.ChangePasswordRequest;
import com.labflow.identity.interfaces.rest.vo.MeVo;
import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.security.CurrentUser;
import com.labflow.shared.web.TraceContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户信息、权限与自助改密。 */
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

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.requireUserId(), request.currentPassword(), request.newPassword());
        // 改密后所有会话（含当前）均已吊销，前端应跳转登录页重新认证
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }
}
