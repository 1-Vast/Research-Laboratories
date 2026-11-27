package com.labflow.research;

import com.jayway.jsonpath.JsonPath;
import com.labflow.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目工作流接口测试：真实过滤器链 + 真实数据库。
 *
 * <p>重点不是「happy path 能跑通」，而是三条容易被忽略的防线：
 * 跨实验室创建被拒、非成员读详情被拒、旧版本号写入返回 409。</p>
 */
class ProjectApiTest extends ApiTestSupport {

    private static final String LEADER = "leader_demo";
    private static final String PI = "pi_demo";
    private static final String RESEARCHER_A = "researcher_a";
    private static final String RESEARCHER_B = "researcher_b";

    @Autowired
    private JdbcTemplate jdbc;

    private long labId(String code) {
        return jdbc.queryForObject("select id from labs where code = ?", Long.class, code);
    }

    private long userId(String username) {
        return jdbc.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private Map<String, Object> projectBody(long labId, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("labId", labId);
        body.put("name", name);
        body.put("objective", "验证项目立项与审批闭环");
        body.put("plannedStart", "2026-11-01");
        body.put("plannedEnd", "2027-03-31");
        return body;
    }

    private long createProject(String token, long labId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectBody(labId, name))))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    @Test
    @DisplayName("负责人在自己实验室创建项目：返回草稿，版本从 0 开始")
    void leaderCreatesDraftProject() throws Exception {
        String token = login(LEADER, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectBody(labId("LAB-A"), "智能感知样机研制"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.leaderId").value(userId(LEADER)));
    }

    @Test
    @DisplayName("不能在自己不属于的实验室创建项目")
    void cannotCreateInLabWithoutMembership() throws Exception {
        String token = login(LEADER, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectBody(labId("LAB-B"), "跨实验室越权项目"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB_SCOPE_FORBIDDEN"));
    }

    @Test
    @DisplayName("科研人员没有 project:create 权限，创建直接被拒")
    void researcherCannotCreateProject() throws Exception {
        String token = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectBody(labId("LAB-A"), "科研人员越权建项目"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("完整闭环：草稿 → 提交 → PI 审批 → 进行中")
    void fullApprovalFlow() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "多模态感知融合");

        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.version").value(1));

        String pi = login(PI, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + id + "/approve")
                        .header("Authorization", "Bearer " + pi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));

        // 审批轨迹留痕：答辩时可展示谁在什么时候批准
        mockMvc.perform(get("/api/v1/projects/" + id + "/reviews")
                        .header("Authorization", "Bearer " + pi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reviewType").value("INIT"))
                .andExpect(jsonPath("$.data[0].decision").value("APPROVE"))
                .andExpect(jsonPath("$.data[0].reviewerId").value(userId(PI)));
    }

    @Test
    @DisplayName("项目负责人没有审批权限，无法自批")
    void leaderCannotApprove() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "自批测试项目");
        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + id + "/approve")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "version", 1))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用过期版本号修改项目返回 409，避免覆盖他人审批结果")
    void staleVersionIsRejected() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "乐观锁测试项目");
        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "改名尝试", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("驳回必须填写理由，缺理由返回 400")
    void rejectRequiresReason() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "驳回理由校验项目");
        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        String pi = login(PI, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + id + "/reject")
                        .header("Authorization", "Bearer " + pi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECT", "version", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("被驳回的项目可重新编辑并再次提交")
    void rejectedProjectCanReopen() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "驳回重开项目");
        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        String pi = login(PI, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + id + "/reject")
                        .header("Authorization", "Bearer " + pi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECT", "reason", "目标不够聚焦", "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/projects/" + id + "/reopen")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("非项目成员读取详情返回 403，而不是 404 泄露存在性")
    void nonMemberCannotReadDetail() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "成员边界测试项目");

        String outsider = login(RESEARCHER_B, DEMO_PASSWORD);
        mockMvc.perform(get("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_REQUIRED"));
    }

    @Test
    @DisplayName("立项通过后负责人可加成员，成员随后即可读取详情")
    void addedMemberCanRead() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "成员授权测试项目");

        // 成员维护只在进行中的项目开放：草稿态先提交并审批通过
        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());
        String pi = login(PI, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + id + "/approve")
                        .header("Authorization", "Bearer " + pi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "version", 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + id + "/members")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId(RESEARCHER_A), "memberType", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberType").value("MEMBER"));

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(get("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.id").value(id))
                .andExpect(jsonPath("$.data.progress.total").value(0));
    }

    @Test
    @DisplayName("草稿状态不允许维护成员，返回 409")
    void draftProjectRejectsMemberChange() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "草稿成员限制项目");

        mockMvc.perform(post("/api/v1/projects/" + id + "/members")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId(RESEARCHER_A)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("只有草稿可以删除，已提交的项目必须留痕")
    void onlyDraftIsDeletable() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "删除限制测试项目");
        mockMvc.perform(post("/api/v1/projects/" + id + "/submit")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("列表按作用域过滤：LAB-B 的科研人员看不到 LAB-A 的项目")
    void listIsScoped() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        createProject(leader, labId("LAB-A"), "作用域过滤项目");

        String outsider = login(RESEARCHER_B, DEMO_PASSWORD);
        MvcResult result = mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isOk())
                .andReturn();
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        assertThat(total.longValue()).isZero();
    }

    @Test
    @DisplayName("PI 能看到本实验室全部项目，即使不是项目成员")
    void piSeesLabProjects() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long id = createProject(leader, labId("LAB-A"), "PI 可见性测试项目");

        String pi = login(PI, DEMO_PASSWORD);
        mockMvc.perform(get("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + pi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.id").value(id));

        mockMvc.perform(get("/api/v1/projects/" + id + "/permissions")
                        .header("Authorization", "Bearer " + pi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labPi").value(true))
                .andExpect(jsonPath("$.data.leader").value(false));
    }
}
