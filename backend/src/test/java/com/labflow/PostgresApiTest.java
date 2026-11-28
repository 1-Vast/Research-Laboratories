package com.labflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 PostgreSQL 集成测试：不依赖 Docker，测试启动时拉起一个临时 PostgreSQL 实例，
 * 由 Flyway 执行与生产完全相同的迁移脚本，再跑通认证、RBAC 与审计主链路。
 * <p>目的：证明生产 profile（PostgreSQL）不是"文档里写着"，而是真实可运行。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pgtest")
@DisplayName("PostgreSQL 集成")
class PostgresApiTest {

    private static final String DEMO_PASSWORD = "Labflow@2026";
    private static EmbeddedPostgres postgres;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) throws IOException {
        // 默认等待 10 秒，在 CI 或本机高负载时冷启动 initdb 会超时（Gave up waiting for server to start），
        // 这里放宽到 120 秒；启动失败仍会明确抛错，不会掩盖真实问题。
        postgres = EmbeddedPostgres.builder()
                .setPGStartupWait(java.time.Duration.ofSeconds(120))
                .start();
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    @DisplayName("确认运行在真实 PostgreSQL 上")
    void runsOnRealPostgres() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        assertThat(version).contains("PostgreSQL");
        String database = jdbcTemplate.queryForObject("select current_database()", String.class);
        assertThat(database).isEqualTo("postgres");
    }

    @Test
    @DisplayName("Flyway 在 PostgreSQL 上建出 17 张表与完整种子数据")
    void flywayMigratesSchemaOnPostgres() {
        Integer tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(tables).isEqualTo(17);

        assertThat(count("roles")).isEqualTo(4);
        assertThat(count("permissions")).isEqualTo(49);
        Integer seededUsers = jdbcTemplate.queryForObject("""
                select count(*) from users
                where username in ('admin_demo','pi_demo','leader_demo','researcher_a','researcher_b')
                """, Integer.class);
        assertThat(seededUsers).isEqualTo(5);
        assertThat(count("labs")).isEqualTo(2);
        assertThat(count("flyway_schema_history")).isGreaterThanOrEqualTo(3);

        // 第二阶段新增的 7 张业务表必须真实存在（空表也要建出来）
        for (String table : java.util.List.of("projects", "project_members", "project_reviews",
                "tasks", "task_submissions", "reports", "report_tasks")) {
            assertThat(count(table)).isZero();
        }
    }

    @Test
    @DisplayName("认证闭环在 PostgreSQL 上可用：登录 → 当前用户 → 退出后旧令牌 401")
    void authenticationWorksOnPostgres() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "researcher_a", "password", DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("researcher_a"))
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].code").value("RESEARCHER"));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        Integer revoked = jdbcTemplate.queryForObject(
                "select count(*) from auth_sessions where revoked_at is not null", Integer.class);
        assertThat(revoked).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("唯一约束在 PostgreSQL 上生效：重复用户名返回 409")
    void uniqueConstraintEnforcedOnPostgres() throws Exception {
        String username = "pgdup" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        register(username);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Passw0rd1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where username = ?", Integer.class, username);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("禁用账号在 PostgreSQL 上立即失效（auth_version + 会话吊销）")
    void disablingUserInvalidatesTokenOnPostgres() throws Exception {
        String adminToken = login("admin_demo", DEMO_PASSWORD);
        String username = "pgdisable" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long userId = register(username);
        String userToken = login(username, "Passw0rd1");

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        Integer version = jdbcTemplate.queryForObject(
                "select auth_version from users where id = ?", Integer.class, userId);
        assertThat(version).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("动态权限与审计在 PostgreSQL 上生效")
    void dynamicPermissionAndAuditWorkOnPostgres() throws Exception {
        String adminToken = login("admin_demo", DEMO_PASSWORD);
        String roleCode = "PG_ROLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        MvcResult createdRole = mockMvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", roleCode, "name", "PG 测试角色"))))
                .andExpect(status().isCreated())
                .andReturn();
        long roleId = ((Number) JsonPath.read(createdRole.getResponse().getContentAsString(), "$.data.id")).longValue();

        String username = "pgrole" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Passw0rd1",
                                "roles", List.of(roleCode)))))
                .andExpect(status().isCreated());
        String userToken = login(username, "Passw0rd1");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/admin/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("permissions", List.of("report:read")))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("report:read"));

        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_events where action in ('auth.login', 'role.assignPermissions')",
                Integer.class);
        assertThat(auditRows).isGreaterThanOrEqualTo(1);
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return value == null ? -1 : value;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private long register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Passw0rd1",
                                "displayName", "PG 测试用户"))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
