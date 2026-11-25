package com.labflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口测试基类：真实 Spring 上下文 + 真实过滤器链 + 真实数据库迁移，
 * 因此 401/403/退出失效等结论都是经过安全链路验证的，而不是绕过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String DEMO_PASSWORD = "Labflow@2026";
    protected static final String ADMIN_USERNAME = "admin_demo";
    protected static final String RESEARCHER_USERNAME = "researcher_a";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void ensureContext() {
        // 占位：子类可覆盖，保留统一生命周期
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    protected ResultActions postJsonWithToken(String url, Object body, String token) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json(body)));
    }

    protected ResultActions getWithToken(String url, String token) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", token == null ? "" : "Bearer " + token));
    }

    /** 登录并返回 access token。 */
    protected String login(String username, String password) throws Exception {
        MvcResult result = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", password))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    protected String loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, DEMO_PASSWORD);
    }

    protected String loginAsResearcher() throws Exception {
        return login(RESEARCHER_USERNAME, DEMO_PASSWORD);
    }

    protected String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected long registerUser(String username) throws Exception {
        MvcResult result = postJson("/api/v1/auth/register", Map.of(
                "username", username,
                "password", "Passw0rd1",
                "displayName", "测试用户",
                "email", username + "@labflow.test"))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }
}
