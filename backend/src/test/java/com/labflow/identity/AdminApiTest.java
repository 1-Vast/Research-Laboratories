package com.labflow.identity;

import com.jayway.jsonpath.JsonPath;
import com.labflow.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理接口的列表能力（排序/分页边界）与用户、角色的维护操作。 */
@DisplayName("管理接口维护")
class AdminApiTest extends ApiTestSupport {

    @Test
    @DisplayName("用户列表支持白名单排序（降序）")
    void userListSupportsSorting() throws Exception {
        String token = loginAsAdmin();
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users?sort=username,desc&size=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<String> usernames = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.items[*].username");
        assertThat(usernames).hasSizeGreaterThan(1)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("角色列表支持白名单排序（升序）")
    void roleListSupportsSorting() throws Exception {
        String token = loginAsAdmin();
        MvcResult result = mockMvc.perform(get("/api/v1/admin/roles?sort=code,asc&size=50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<String> codes = JsonPath.read(result.getResponse().getContentAsString(), "$.data.items[*].code");
        assertThat(codes).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("非法排序字段被拒绝，避免把前端字符串直接拼进排序")
    void nonWhitelistedSortFieldRejected() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get("/api/v1/admin/users?sort=passwordHash,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("排序字段")));
    }

    @Test
    @DisplayName("非法排序方向被拒绝")
    void invalidSortDirectionRejected() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get("/api/v1/admin/roles?sort=code,up")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("分页参数越界被拒绝")
    void paginationBoundsAreValidated() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get("/api/v1/admin/users?page=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/users?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("修改用户资料")
    void adminUpdatesUserProfile() throws Exception {
        String token = loginAsAdmin();
        long userId = registerUser(uniqueUsername("profile"));

        mockMvc.perform(patch("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "新的昵称", "email", "new@labflow.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("新的昵称"))
                .andExpect(jsonPath("$.data.email").value("new@labflow.test"));
    }

    @Test
    @DisplayName("删除用户为逻辑删除：立即失效且无法再登录")
    void adminDeletesUserLogically() throws Exception {
        String adminToken = loginAsAdmin();
        String username = uniqueUsername("todelete");
        long userId = registerUser(username);
        String userToken = login(username, "Passw0rd1");

        mockMvc.perform(delete("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/login", Map.of("username", username, "password", "Passw0rd1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("自定义角色可改名，内置角色不可改名")
    void roleRenameRules() throws Exception {
        String token = loginAsAdmin();
        String roleCode = "TMP_" + uniqueUsername("rename").toUpperCase();
        MvcResult created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", roleCode, "name", "原名称"))))
                .andExpect(status().isCreated())
                .andReturn();
        long roleId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(patch("/api/v1/admin/roles/" + roleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "新名称", "description", "改名测试"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"));

        long builtinRoleId = roleIdByCode(token, "RESEARCHER");
        mockMvc.perform(patch("/api/v1/admin/roles/" + builtinRoleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "试图改名"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_ROLE"));
    }

    @Test
    @DisplayName("内置管理员角色不能移除平台管理权限")
    void platformAdminPermissionsCannotBeRemoved() throws Exception {
        String token = loginAsAdmin();
        long adminRoleId = roleIdByCode(token, "SYS_ADMIN");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/admin/roles/" + adminRoleId + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("permissions", List.of("audit:read")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("user:manage")));
    }

    private long roleIdByCode(String token, String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/roles?size=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.items[?(@.code == '" + code + "')].id");
        assertThat(ids).isNotEmpty();
        return ids.get(0).longValue();
    }
}
