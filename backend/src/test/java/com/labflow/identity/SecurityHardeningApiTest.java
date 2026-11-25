package com.labflow.identity;

import com.jayway.jsonpath.JsonPath;
import com.labflow.support.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全加固验证：登录失败限流、Cookie 接口来源校验、数据库控制台不外露、
 * 以及「拒绝事件独立落库」的审计闭环。
 */
@DisplayName("安全加固")
class SecurityHardeningApiTest extends ApiTestSupport {

    private static final String TRUSTED_ORIGIN = "http://localhost:5173";

    @Test
    @DisplayName("同一账号连续失败达到阈值后返回 429")
    void loginAttemptsAreThrottled() throws Exception {
        String username = uniqueUsername("brute");
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", Map.of("username", username, "password", "WrongPassw0rd1"))
                    .andExpect(status().isUnauthorized());
        }
        postJson("/api/v1/auth/login", Map.of("username", username, "password", "WrongPassw0rd1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // 达到阈值后即使口令正确也先拒绝，避免在线暴力破解
        postJson("/api/v1/auth/login", Map.of("username", username, "password", "Passw0rd1"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("成功登录会清零失败计数")
    void successfulLoginResetsThrottle() throws Exception {
        String username = uniqueUsername("reset");
        registerUser(username);
        for (int i = 0; i < 4; i++) {
            postJson("/api/v1/auth/login", Map.of("username", username, "password", "WrongPassw0rd1"))
                    .andExpect(status().isUnauthorized());
        }
        login(username, "Passw0rd1");
        postJson("/api/v1/auth/login", Map.of("username", username, "password", "WrongPassw0rd1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("刷新接口在跨站来源下被拒绝，且不轮换令牌")
    void refreshRejectsUntrustedOrigin() throws Exception {
        MvcResult loginResult = postJson("/api/v1/auth/login", Map.of(
                "username", RESEARCHER_USERNAME, "password", DEMO_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("labflow_refresh");

        // 浏览器跨站请求带 Origin：CORS 层直接拒绝（403），请求不会到达业务代码
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie)
                        .header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());

        // 非 Origin 场景（例如被篡改的 Referer）由应用层来源校验拦截，返回统一业务码
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie)
                        .header("Referer", "http://evil.example/attack.html"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 被拒绝的请求没有消耗令牌，受信任来源仍可正常刷新
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie)
                        .header("Origin", TRUSTED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("退出接口拒绝不受信任来源，令牌仍然有效")
    void logoutRejectsUntrustedOrigin() throws Exception {
        String token = loginAsResearcher();
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)
                        .header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("测试/生产 profile 下数据库控制台不对匿名开放")
    void h2ConsoleIsNotPublicOutsideDev() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("越权访问在审计中留痕（独立事务，业务失败不回滚审计）")
    void deniedAccessIsAudited() throws Exception {
        String researcherToken = loginAsResearcher();
        long researcherId = userIdOf(researcherToken);
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + researcherToken))
                .andExpect(status().isForbidden());

        String adminToken = loginAsAdmin();
        MvcResult audit = mockMvc.perform(get("/api/v1/admin/audit-events?action=security.access.denied&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        List<Number> actorIds = JsonPath.read(audit.getResponse().getContentAsString(), "$.data.items[*].actorId");
        assertThat(actorIds).extracting(Number::longValue).contains(researcherId);
    }

    @Test
    @DisplayName("登录失败同样落库，便于发现口令枚举")
    void failedLoginIsAudited() throws Exception {
        String username = uniqueUsername("auditfail");
        postJson("/api/v1/auth/login", Map.of("username", username, "password", "WrongPassw0rd1"))
                .andExpect(status().isUnauthorized());

        String adminToken = loginAsAdmin();
        MvcResult audit = mockMvc.perform(get("/api/v1/admin/audit-events?action=auth.login.failed&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        List<String> metadata = JsonPath.read(audit.getResponse().getContentAsString(), "$.data.items[*].metadata");
        assertThat(metadata).anyMatch(value -> value != null && value.contains(username));
    }

    @Test
    @DisplayName("审计查询按作用域收敛：PI 只能看到本人记录")
    void auditQueryIsScopedForNonAdmin() throws Exception {
        String piToken = login("pi_demo", DEMO_PASSWORD);
        long piId = userIdOf(piToken);

        MvcResult audit = mockMvc.perform(get("/api/v1/admin/audit-events?size=20")
                        .header("Authorization", "Bearer " + piToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> actorIds = JsonPath.read(audit.getResponse().getContentAsString(), "$.data.items[*].actorId");
        assertThat(actorIds).extracting(Number::longValue).isNotEmpty().allMatch(id -> id == piId);
    }

    @Test
    @DisplayName("无 audit:read 的用户无法读取审计")
    void researcherCannotReadAudit() throws Exception {
        String token = loginAsResearcher();
        mockMvc.perform(get("/api/v1/admin/audit-events").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("缺少刷新 Cookie 时来源校验通过仍返回 401")
    void missingRefreshCookieStillUnauthorized() throws Exception {
        assertThatCode(() -> mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", TRUSTED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID")))
                .doesNotThrowAnyException();
    }

    private long userIdOf(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}
