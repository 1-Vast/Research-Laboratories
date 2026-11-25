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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 接口测试：后端强制校验、用户/角色管理、动态权限、最后一个管理员保护。
 * 全部通过真实 URL 与真实过滤器链调用，等价于用 Postman 绕过前端直接调接口。
 */
@DisplayName("RBAC 权限接口")
class RbacApiTest extends ApiTestSupport {

    @Test
    @DisplayName("普通用户调用用户管理接口：403（前端隐藏按钮不算完成）")
    void researcherCannotAccessAdminApi() throws Exception {
        String token = loginAsResearcher();
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员分页查询用户，并支持关键字筛选")
    void adminListsUsersWithPaginationAndSearch() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get("/api/v1/admin/users?page=1&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/users?keyword=researcher")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("管理员创建用户并分配角色，新用户可登录")
    void adminCreatesUserWithRoles() throws Exception {
        String adminToken = loginAsAdmin();
        String username = uniqueUsername("created");

        MvcResult created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", "Passw0rd1",
                                "displayName", "被创建的用户",
                                "roles", List.of("PI")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0].code").value("PI"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();

        long userId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();

        String token = login(username, "Passw0rd1");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.roles[0].code").value("PI"));
    }

    @Test
    @DisplayName("管理员替换用户角色集合")
    void adminAssignsRoles() throws Exception {
        String adminToken = loginAsAdmin();
        String username = uniqueUsername("roles");
        long userId = registerUser(username);

        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("RESEARCHER", "PROJECT_LEADER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2));
    }

    @Test
    @DisplayName("分配未知角色：400")
    void adminRejectsUnknownRole() throws Exception {
        String adminToken = loginAsAdmin();
        long userId = registerUser(uniqueUsername("badrole"));

        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("NO_SUCH_ROLE")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("禁用账号后，该用户已签发的令牌立即 401")
    void disablingUserInvalidatesExistingToken() throws Exception {
        String adminToken = loginAsAdmin();
        String username = uniqueUsername("disable");
        long userId = registerUser(username);
        String userToken = login(username, "Passw0rd1");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        // 禁用后无法再登录，且提示与密码错误一致
        postJson("/api/v1/auth/login", Map.of("username", username, "password", "Passw0rd1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("最后一个系统管理员不可被禁用")
    void lastAdminCannotBeDisabled() throws Exception {
        String adminToken = loginAsAdmin();
        MvcResult me = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long adminId = ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(patch("/api/v1/admin/users/" + adminId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
    }

    @Test
    @DisplayName("角色权限变更后，同一令牌的下一次请求即按新权限生效")
    void permissionChangesTakeEffectImmediately() throws Exception {
        String adminToken = loginAsAdmin();
        String roleCode = "TMP_" + uniqueUsername("role").toUpperCase();

        MvcResult createdRole = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", roleCode, "name", "临时角色", "description", "权限生效测试"))))
                .andExpect(status().isCreated())
                .andReturn();
        long roleId = ((Number) JsonPath.read(createdRole.getResponse().getContentAsString(), "$.data.id")).longValue();

        String username = uniqueUsername("dynperm");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", "Passw0rd1",
                                "roles", List.of(roleCode)))))
                .andExpect(status().isCreated());

        String userToken = login(username, "Passw0rd1");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(0));

        // 管理员动态授予一个业务权限
        mockMvc.perform(put("/api/v1/admin/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("permissions", List.of("report:read", "task:read")))))
                .andExpect(status().isOk());

        // 同一个旧令牌，下一次请求即看到新权限
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("report:read"))
                .andExpect(jsonPath("$.data.permissions[1]").value("task:read"));
    }

    @Test
    @DisplayName("内置角色不可删除")
    void protectedRoleCannotBeDeleted() throws Exception {
        String adminToken = loginAsAdmin();
        long researcherRoleId = roleIdByCode(adminToken, "RESEARCHER");

        mockMvc.perform(delete("/api/v1/admin/roles/" + researcherRoleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_ROLE"));
    }

    @Test
    @DisplayName("仍被用户引用的角色不可删除")
    void roleInUseCannotBeDeleted() throws Exception {
        String adminToken = loginAsAdmin();
        String roleCode = "TMP_" + uniqueUsername("use").toUpperCase();

        MvcResult createdRole = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", roleCode, "name", "被引用角色"))))
                .andExpect(status().isCreated())
                .andReturn();
        long roleId = ((Number) JsonPath.read(createdRole.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", uniqueUsername("roleuser"), "password", "Passw0rd1",
                                "roles", List.of(roleCode)))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/roles/" + roleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));
    }

    @Test
    @DisplayName("角色编码重复：409")
    void duplicateRoleCodeIsRejected() throws Exception {
        String adminToken = loginAsAdmin();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "RESEARCHER", "name", "重复角色"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ROLE_CODE"));
    }

    @Test
    @DisplayName("权限目录接口返回完整权限码清单")
    void permissionsCatalogIsExposed() throws Exception {
        String adminToken = loginAsAdmin();
        MvcResult result = mockMvc.perform(get("/api/v1/admin/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        List<?> codes = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].code");
        assertThat(codes).hasSizeGreaterThanOrEqualTo(40);
        assertThat(codes.toString()).contains("project:approve").contains("task:review");
    }

    @Test
    @DisplayName("参数校验失败：400 并返回字段级错误")
    void validationErrorReturnsFieldDetails() throws Exception {
        String adminToken = loginAsAdmin();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "x", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").exists());
    }

    @Test
    @DisplayName("不存在的路径：404 而不是 500")
    void unknownPathReturns404() throws Exception {
        String adminToken = loginAsAdmin();
        mockMvc.perform(get("/api/v1/not-exists").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private long roleIdByCode(String adminToken, String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/roles?size=100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.items[?(@.code == '" + code + "')].id");
        assertThat(ids).isNotEmpty();
        return ids.get(0).longValue();
    }
}
