package com.labflow.identity;

import com.jayway.jsonpath.JsonPath;
import com.labflow.support.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与授权接口测试：注册、登录、退出、刷新、当前用户。
 * 重点验证课程要求的三条红线：密码不外泄、登录失败提示统一、退出后旧令牌真实失效。
 */
@DisplayName("认证接口")
class AuthApiTest extends ApiTestSupport {

    @Test
    @DisplayName("注册成功：分配默认最低权限角色，响应不含密码字段")
    void registerAssignsDefaultRoleAndHidesPassword() throws Exception {
        String username = uniqueUsername("reg");
        postJson("/api/v1/auth/register", Map.of(
                "username", username,
                "password", "Passw0rd1",
                "displayName", "注册测试",
                "email", username + "@labflow.test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.roles[0].code").value("RESEARCHER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("注册重名：返回 409，数据库唯一约束兜底")
    void registerRejectsDuplicateUsername() throws Exception {
        String username = uniqueUsername("dup");
        registerUser(username);

        postJson("/api/v1/auth/register", Map.of(
                "username", username,
                "password", "Passw0rd1",
                "displayName", "重复注册"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    @DisplayName("用户名大小写与空白归一化后仍视为同一账号")
    void registerNormalizesUsername() throws Exception {
        String username = uniqueUsername("norm");
        registerUser(username);

        postJson("/api/v1/auth/register", Map.of(
                "username", username.toUpperCase(),
                "password", "Passw0rd1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    @DisplayName("弱密码：长度不足或缺少数字均被后端拒绝")
    void registerRejectsWeakPassword() throws Exception {
        postJson("/api/v1/auth/register", Map.of(
                "username", uniqueUsername("weak1"),
                "password", "abc123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        postJson("/api/v1/auth/register", Map.of(
                "username", uniqueUsername("weak2"),
                "password", "abcdefgh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("密码必须同时包含字母和数字"));
    }

    @Test
    @DisplayName("非法用户名：400 且返回字段级错误")
    void registerRejectsInvalidUsername() throws Exception {
        postJson("/api/v1/auth/register", Map.of(
                "username", "ab",
                "password", "Passw0rd1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").exists());
    }

    @Test
    @DisplayName("登录成功：签发 access token 并下发 HttpOnly 刷新 Cookie")
    void loginReturnsTokenAndRefreshCookie() throws Exception {
        MvcResult result = postJson("/api/v1/auth/login", Map.of(
                "username", RESEARCHER_USERNAME,
                "password", DEMO_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.user.username").value(RESEARCHER_USERNAME))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(cookie().exists("labflow_refresh"))
                .andExpect(cookie().httpOnly("labflow_refresh", true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Object permissions = JsonPath.read(body, "$.data.user.permissions");
        assertThat(permissions.toString()).contains("report:create").contains("task:submit");
    }

    @Test
    @DisplayName("密码错误：401 且提示统一")
    void loginFailsWithUnifiedMessageForWrongPassword() throws Exception {
        postJson("/api/v1/auth/login", Map.of(
                "username", RESEARCHER_USERNAME,
                "password", "WrongPassw0rd1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("用户不存在：与密码错误返回完全一致的状态与提示")
    void loginFailsWithSameMessageForUnknownUser() throws Exception {
        postJson("/api/v1/auth/login", Map.of(
                "username", "no_such_user_here",
                "password", DEMO_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口：401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("伪造令牌访问受保护接口：401")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("当前用户接口：返回角色与权限清单")
    void meReturnsRolesAndPermissions() throws Exception {
        String token = loginAsResearcher();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(RESEARCHER_USERNAME))
                .andExpect(jsonPath("$.data.roles[0].code").value("RESEARCHER"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.authVersion").isNumber());
    }

    @Test
    @DisplayName("退出登录：服务端吊销会话，旧 access token 立即 401")
    void logoutInvalidatesOldAccessToken() throws Exception {
        MvcResult loginResult = postJson("/api/v1/auth/login", Map.of(
                "username", RESEARCHER_USERNAME,
                "password", DEMO_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");

        // 退出前令牌可用
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("labflow_refresh", 0));

        // 关键断言：不是前端删 token，而是服务端会话已吊销
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    @DisplayName("刷新令牌：轮换成功，重放旧令牌会吊销整个会话")
    void refreshRotatesTokenAndRevokesSessionOnReplay() throws Exception {
        MvcResult loginResult = postJson("/api/v1/auth/login", Map.of(
                "username", RESEARCHER_USERNAME,
                "password", DEMO_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        Cookie oldRefresh = loginResult.getResponse().getCookie("labflow_refresh");
        assertThat(oldRefresh).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String newAccessToken = JsonPath.read(refreshResult.getResponse().getContentAsString(),
                "$.data.accessToken");
        assertThat(refreshResult.getResponse().getCookie("labflow_refresh")).isNotNull();

        // 旧刷新令牌重放：401，且会话族被吊销
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("缺少刷新 Cookie 时刷新接口返回 401")
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
