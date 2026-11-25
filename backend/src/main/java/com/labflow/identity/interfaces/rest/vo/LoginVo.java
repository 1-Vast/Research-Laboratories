package com.labflow.identity.interfaces.rest.vo;

/** 登录/刷新响应。refresh token 在 HttpOnly Cookie 中，不出现在响应体。 */
public record LoginVo(String accessToken, String tokenType, long expiresIn, MeVo user) {
}
