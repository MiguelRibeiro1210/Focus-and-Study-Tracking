package com.focusdesktop.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "focusdesktop.data-dir=target/test-api-data",
        "focusdesktop.api.token=test-token",
        "spring.datasource.url=jdbc:h2:mem:session_api_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class SessionControllerIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM session_interval");
        jdbc.update("DELETE FROM focus_session");
        jdbc.update("DELETE FROM domain_event_outbox");
    }

    @Test
    void healthDoesNotRequireToken() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        mvc.perform(get("/api/v1/sessions/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        mvc.perform(get("/api/v1/sessions/active").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsNonLocalOrigins() throws Exception {
        mvc.perform(get("/api/v1/sessions/active")
                        .header("Origin", "https://example.com")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ORIGIN"));
    }

    @Test
    void startsSessionAndReturnsFlatResponseThenCanPauseIt() throws Exception {
        String sessionId = mvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer test-token")
                        .contentType("application/json")
                        .content("{\"sessionType\":\"WORK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.remainingSeconds").value(60))
                .andReturn().getResponse().getContentAsString();

        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(sessionId).get("sessionId").asText();
        mvc.perform(post("/api/v1/sessions/{id}/pause", id)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"));

        mvc.perform(get("/api/v1/sessions/active")
                        .header("Authorization", "Bearer test-token")
                        .header("Origin", "tauri://localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id));
    }

    @Test
    void returnsNoContentWhenThereIsNoActiveSession() throws Exception {
        mvc.perform(get("/api/v1/sessions/active").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
    }
}
