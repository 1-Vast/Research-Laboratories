package com.labflow.identity.application;

/** 登录/刷新结果：access token 由前端保存在内存，refresh token 由 Controller 写入 HttpOnly Cookie。 */
public record AuthResult(String accessToken, long expiresInSeconds, String refreshToken,
                         com.labflow.identity.domain.User user) {
}
