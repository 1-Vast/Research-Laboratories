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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务工作流接口测试。
 *
 * <p>关注两类容易漏的规则：任务只能由受派人推进（否则任何人都能替别人交付），
 * 以及任务状态如何反向影响项目进度与结项资格。</p>
 */
class TaskApiTest extends ApiTestSupport {

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

    /** 建一个「进行中且 researcher_a 已是成员」的项目，任务相关用例都从它出发。 */
    private long activeProjectWithMember(String token, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("labId", labId("LAB-A"));
        body.put("name", name);
        body.put("objective", "任务流测试");
        MvcResult created = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"))
                .longValue();

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        String pi = login(PI, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/approve")
                        .header("Authorization", "Bearer " + pi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "version", 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId(RESEARCHER_A)))))
                .andExpect(status().isCreated());
        return projectId;
    }

    private Map<String, Object> taskBody(long assigneeId, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("assigneeId", assigneeId);
        body.put("title", title);
        body.put("description", "完成信号采集与标定");
        body.put("priority", "HIGH");
        return body;
    }

    private long createTask(String token, long projectId, long assigneeId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(taskBody(assigneeId, title))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    @Test
    @DisplayName("负责人给项目成员分派任务，初始为未开始")
    void leaderAssignsTask() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "任务分派项目");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(taskBody(userId(RESEARCHER_A), "传感器标定"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    @DisplayName("不能把任务分派给非项目成员")
    void cannotAssignToNonMember() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "非成员分派项目");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(taskBody(userId(RESEARCHER_B), "越界分派"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("草稿项目不能创建任务")
    void cannotCreateTaskInDraftProject() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("labId", labId("LAB-A"));
        body.put("name", "草稿任务项目");
        MvcResult created = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"))
                .longValue();

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(taskBody(userId(LEADER), "草稿任务"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("受派人可以开始任务，其他人不行")
    void onlyAssigneeCanStart() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "任务开始项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "开始校验任务");

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        long another = createTask(leader, projectId, userId(RESEARCHER_A), "他人代开始任务");
        mockMvc.perform(post("/api/v1/tasks/" + another + "/start")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("提交交付生成快照，重复提交号递增且历史保留")
    void submitKeepsSnapshotHistory() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "交付快照项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "交付快照任务");

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "第一次交付：完成标定", "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 负责人退回，受派人重新提交，应产生第 2 条快照
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/review")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECT", "note", "缺少误差分析", "version", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 3))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "第二次交付：补充误差分析", "version", 4))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/submissions")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].submissionNo").value(1))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("RETURNED"))
                .andExpect(jsonPath("$.data[1].submissionNo").value(2))
                .andExpect(jsonPath("$.data[1].reviewStatus").value("SUBMITTED"));
    }

    @Test
    @DisplayName("退回必须写原因")
    void returnRequiresNote() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "退回理由项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "退回理由任务");

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "交付内容", "version", 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/review")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECT", "version", 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("验收通过后任务计入项目进度")
    void approvedTaskCountsInProgress() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "进度统计项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "进度统计任务");

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "交付完成", "version", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/review")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "version", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.total").value(1))
                .andExpect(jsonPath("$.data.progress.done").value(1))
                .andExpect(jsonPath("$.data.progress.percent").value(100));
    }

    @Test
    @DisplayName("仍有未验收任务时不能申请结项")
    void openTasksBlockCloseRequest() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "结项阻塞项目");
        createTask(leader, projectId, userId(RESEARCHER_A), "未完成任务");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/close-request")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("closeNote", "准备结项", "version", 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPEN_TASKS_EXIST"));
    }

    @Test
    @DisplayName("取消的任务不计入进度分母")
    void canceledTaskExcludedFromProgress() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "取消任务项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "将被取消的任务");

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/cancel")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "需求变更", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.total").value(0))
                .andExpect(jsonPath("$.data.progress.percent").value(0));
    }

    @Test
    @DisplayName("取消任务必须写原因")
    void cancelRequiresReason() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "取消理由项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "取消理由任务");

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/cancel")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", " ", "version", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("非项目成员读取任务详情被拒，防止改 taskId 越权")
    void outsiderCannotReadTask() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "任务越权项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "越权读取任务");

        String outsider = login(RESEARCHER_B, DEMO_PASSWORD);
        mockMvc.perform(get("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_REQUIRED"));
    }

    @Test
    @DisplayName("我的任务列表只返回指派给自己的任务")
    void mineOnlyReturnsOwnTasks() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "我的任务项目");
        createTask(leader, projectId, userId(RESEARCHER_A), "指派给A的任务");

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        MvcResult result = mockMvc.perform(get("/api/v1/tasks/mine")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        Number total = JsonPath.read(body, "$.data.total");
        assertThat(total.longValue()).isGreaterThanOrEqualTo(1);
        java.util.List<Number> assignees = JsonPath.read(body, "$.data.items[*].assigneeId");
        assertThat(assignees).allMatch(id -> id.longValue() == userId(RESEARCHER_A));
    }

    @Test
    @DisplayName("过期版本号操作任务返回 409")
    void staleTaskVersionRejected() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProjectWithMember(leader, "任务版本项目");
        long taskId = createTask(leader, projectId, userId(RESEARCHER_A), "版本校验任务");

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "交付", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }
}
