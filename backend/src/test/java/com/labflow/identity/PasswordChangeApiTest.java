package com.labflow.identity;

import com.jayway.jsonpath.JsonPath;
import com.labflow.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 自助改密：旧密码校验、策略校验、全量会话吊销与审计。 */
@DisplayName("自助改密")
class PasswordChangeApiTest extends ApiTestSupport {

    @Test
    @DisplayName("改密成功：旧令牌立即失效，旧密码不可登录，新密码可登录")
    void changePasswordRevokesAllSessions() throws Exception {
        String username = uniqueUsername("pwd");
        registerUser(username);
        String token = login(username, "Passw0rd1");

        mockMvc.perform(put("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd1", "newPassword", "NewPassw0rd2"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        postJson("/api/v1/auth/login", Map.of("username", username, "password", "Passw0rd1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "NewPassw0rd2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("旧密码错误：拒绝改密，原密码仍可登录")
    void wrongCurrentPasswordRejected() throws Exception {
        String username = uniqueUsername("pwdold");
        registerUser(username);
        String token = login(username, "Passw0rd1");

        mockMvc.perform(put("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "WrongPassw0rd1", "newPassword", "NewPassw0rd2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("当前密码不正确"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        login(username, "Passw0rd1");
    }

    @Test
    @DisplayName("新密码不符合策略：400，且不改动密码")
    void weakNewPasswordRejected() throws Exception {
        String username = uniqueUsername("pwdweak");
        registerUser(username);
        String token = login(username, "Passw0rd1");

        mockMvc.perform(put("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd1", "newPassword", "abcdefgh"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码必须同时包含字母和数字"));

        login(username, "Passw0rd1");
    }

    @Test
    @DisplayName("新密码与旧密码相同：拒绝")
    void samePasswordRejected() throws Exception {
        String username = uniqueUsername("pwdsame");
        registerUser(username);
        String token = login(username, "Passw0rd1");

        mockMvc.perform(put("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd1", "newPassword", "Passw0rd1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能与当前密码相同"));
    }

    @Test
    @DisplayName("缺少必填字段：400 且返回字段级错误")
    void validationErrorsReturned() throws Exception {
        String username = uniqueUsername("pwdvalid");
        registerUser(username);
        String token = login(username, "Passw0rd1");

        mockMvc.perform(put("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd1", "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").exists());
    }

    @Test
    @DisplayName("未认证不能改密")
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(put("/api/v1/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd1", "newPassword", "NewPassw0rd2"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("改密写入审计，可按动作检索")
    void passwordChangeIsAudited() throws Exception {
        String username = uniqueUsername("pwdaudit");
        long userId = registerUser(username);
        String token = login(username, "Passw0rd1");

        mockMvc.perform(put("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd1", "newPassword", "NewPassw0rd2"))))
                .andExpect(status().isNoContent());

        String adminToken = loginAsAdmin();
        MvcResult audit = mockMvc.perform(get("/api/v1/admin/audit-events?action=auth.password.change&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> actorIds = JsonPath.read(audit.getResponse().getContentAsString(),
                "$.data.items[*].actorId");
        assertThat(actorIds).extracting(Number::longValue).contains(userId);
    }
}
