package com.labflow.identity.interfaces.rest;

import com.labflow.identity.application.AuthResult;
import com.labflow.identity.application.AuthService;
import com.labflow.identity.application.command.LoginCommand;
import com.labflow.identity.application.command.RegisterCommand;
import com.labflow.identity.domain.User;
import com.labflow.identity.interfaces.rest.dto.LoginRequest;
import com.labflow.identity.interfaces.rest.dto.RegisterRequest;
import com.labflow.identity.interfaces.rest.vo.LoginVo;
import com.labflow.identity.interfaces.rest.vo.UserVo;
import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import com.labflow.shared.security.CurrentUser;
import com.labflow.shared.security.RefreshCookieSupport;
import com.labflow.shared.security.TrustedOriginValidator;
import com.labflow.shared.web.TraceContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieSupport cookieSupport;
    private final TrustedOriginValidator trustedOriginValidator;

    public AuthController(AuthService authService, RefreshCookieSupport cookieSupport,
                          TrustedOriginValidator trustedOriginValidator) {
        this.authService = authService;
        this.cookieSupport = cookieSupport;
        this.trustedOriginValidator = trustedOriginValidator;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserVo> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(new RegisterCommand(request.username(), request.password(),
                request.email(), request.displayName()));
        return ApiResponse.ok(IdentityWebMapper.toUserVo(user), TraceContext.currentTraceId());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginVo>> login(@Valid @RequestBody LoginRequest request,
                                                      HttpServletRequest httpRequest) {
        AuthResult result = authService.login(new LoginCommand(request.username(), request.password(),
                httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT)));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSupport.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(toLoginVo(result), TraceContext.currentTraceId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginVo>> refresh(HttpServletRequest httpRequest) {
        // 刷新依赖浏览器自动携带的 Cookie，先校验来源，防止第三方站点诱导请求
        trustedOriginValidator.assertTrusted(httpRequest);
        String rawRefreshToken = extractRefreshToken(httpRequest);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "缺少刷新令牌");
        }
        AuthResult result = authService.refresh(rawRefreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSupport.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(toLoginVo(result), TraceContext.currentTraceId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        trustedOriginValidator.assertTrusted(httpRequest);
        var principal = CurrentUser.require();
        authService.logout(principal.sessionId(), principal.userId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString())
                .body(null);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieSupport.cookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private LoginVo toLoginVo(AuthResult result) {
        return new LoginVo(result.accessToken(), "Bearer", result.expiresInSeconds(),
                IdentityWebMapper.toMeVo(result.user()));
    }
}
