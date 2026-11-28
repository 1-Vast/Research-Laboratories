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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 周报工作流接口测试。
 *
 * <p>周报最容易出的两个问题：同一周重复提交、提交后还能被改。两条都在这里钉死。</p>
 */
class ReportApiTest extends ApiTestSupport {

    private static final String LEADER = "leader_demo";
    private static final String PI = "pi_demo";
    private static final String RESEARCHER_A = "researcher_a";
    private static final String RESEARCHER_B = "researcher_b";
    private static final String WEEK = "2026-11-25";

    @Autowired
    private JdbcTemplate jdbc;

    private long labId(String code) {
        return jdbc.queryForObject("select id from labs where code = ?", Long.class, code);
    }

    private long userId(String username) {
        return jdbc.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private long activeProject(String token, String name, String memberUsername) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("labId", labId("LAB-A"));
        body.put("name", name);
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
        if (memberUsername != null) {
            mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("userId", userId(memberUsername)))))
                    .andExpect(status().isCreated());
        }
        return projectId;
    }

    private Map<String, Object> reportBody(String week) {
        Map<String, Object> body = new HashMap<>();
        body.put("weekStart", week);
        body.put("work", "完成信号采集模块联调");
        body.put("blockers", "标定设备排期紧张");
        body.put("nextPlan", "补充误差分析");
        return body;
    }

    private long createReport(String token, long projectId, String week) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reportBody(week))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    @Test
    @DisplayName("成员写周报，周起始日自动归一到周一")
    void createNormalizesWeekStart() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报归一项目", RESEARCHER_A);

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reportBody("2026-11-25"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.weekStart").value("2026-11-23"))
                .andExpect(jsonPath("$.data.authorId").value(userId(RESEARCHER_A)));
    }

    @Test
    @DisplayName("同项目同作者同一周只能有一份周报")
    void duplicateWeekRejected() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报唯一项目", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        createReport(member, projectId, WEEK);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reportBody("2026-11-27"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT_WEEK"));
    }

    @Test
    @DisplayName("非项目成员不能写该项目周报")
    void nonMemberCannotCreate() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报越权项目", null);

        String outsider = login(RESEARCHER_B, DEMO_PASSWORD);
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reportBody(WEEK))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_REQUIRED"));
    }

    @Test
    @DisplayName("提交后负责人审核通过")
    void submitAndApprove() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报审批项目", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        long reportId = createReport(member, projectId, WEEK);

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "note", "记录清晰", "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewerId").value(userId(LEADER)));
    }

    @Test
    @DisplayName("已提交的周报不能再修改，避免审核对象被替换")
    void submittedReportIsReadOnly() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报只读项目", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        long reportId = createReport(member, projectId, WEEK);
        mockMvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("work", "偷偷改内容", "version", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("退回必须写意见，退回后作者可修改并重新提交")
    void returnThenResubmit() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报退回项目", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        long reportId = createReport(member, projectId, WEEK);
        mockMvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECT", "version", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECT", "note", "缺少量化指标", "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        mockMvc.perform(put("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("work", "补充量化指标：误差 0.3%", "version", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("只有草稿可以删除")
    void onlyDraftDeletable() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报删除项目", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        long reportId = createReport(member, projectId, WEEK);
        mockMvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("周报只能关联同项目的任务")
    void cannotLinkForeignTask() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectA = activeProject(leader, "周报关联项目A", RESEARCHER_A);
        long projectB = activeProject(leader, "周报关联项目B", RESEARCHER_A);
        MvcResult taskResult = mockMvc.perform(post("/api/v1/projects/" + projectB + "/tasks")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assigneeId", userId(RESEARCHER_A), "title", "别的项目的任务"))))
                .andExpect(status().isCreated())
                .andReturn();
        long foreignTask = ((Number) JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.id"))
                .longValue();

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        Map<String, Object> body = reportBody(WEEK);
        body.put("taskIds", List.of(foreignTask));
        mockMvc.perform(post("/api/v1/projects/" + projectA + "/reports")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("周报可以关联本项目任务并在详情回显")
    void linksOwnTask() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报关联本项目", RESEARCHER_A);
        MvcResult taskResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assigneeId", userId(RESEARCHER_A), "title", "本周任务"))))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = ((Number) JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.id"))
                .longValue();

        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        Map<String, Object> body = reportBody(WEEK);
        body.put("taskIds", List.of(taskId));
        MvcResult created = mockMvc.perform(post("/api/v1/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        long reportId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"))
                .longValue();

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskIds[0]").value(taskId));

        // 列表也必须带回 taskIds：前端编辑草稿时直接复用列表行，
        // 若列表返回空数组，保存时会把已有关联任务清空（静默丢数据）。
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].taskIds[0]").value(taskId));
    }

    @Test
    @DisplayName("非成员不能读取周报详情")
    void outsiderCannotRead() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报读取边界", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        long reportId = createReport(member, projectId, WEEK);

        String outsider = login(RESEARCHER_B, DEMO_PASSWORD);
        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_REQUIRED"));
    }

    @Test
    @DisplayName("过期版本号提交周报返回 409")
    void staleVersionRejected() throws Exception {
        String leader = login(LEADER, DEMO_PASSWORD);
        long projectId = activeProject(leader, "周报版本项目", RESEARCHER_A);
        String member = login(RESEARCHER_A, DEMO_PASSWORD);
        long reportId = createReport(member, projectId, WEEK);

        mockMvc.perform(put("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("work", "改一次", "version", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }
}
