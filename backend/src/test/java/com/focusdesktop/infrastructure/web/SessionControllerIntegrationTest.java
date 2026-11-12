package com.focusdesktop.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

    @Autowired
    private FocusSessionRepository sessions;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    void startsPendingIntervalSuccessfully() throws Exception {
        UUID id = seedWaitingSession(SessionType.WORK);

        mvc.perform(post("/api/v1/sessions/{id}/intervals/start", id)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id.toString()))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.intervalType").value("WORK"))
                .andExpect(jsonPath("$.intervalState").value("RUNNING"));
    }

    @Test
    void skipsPendingBreakSuccessfully() throws Exception {
        UUID id = seedWaitingSession(SessionType.SHORT_BREAK);

        mvc.perform(post("/api/v1/sessions/{id}/skip-break", id)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id.toString()))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.intervalType").value("WORK"))
                .andExpect(jsonPath("$.intervalState").value("RUNNING"));
    }

    @Test
    void mapsInvalidIntervalRouteStatesAndMissingSessions() throws Exception {
        String id = mvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer test-token")
                        .contentType("application/json")
                        .content("{\"sessionType\":\"WORK\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String runningId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(id).get("sessionId").asText();

        mvc.perform(post("/api/v1/sessions/{id}/intervals/start", runningId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));
        mvc.perform(post("/api/v1/sessions/{id}/skip-break", runningId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));
        mvc.perform(post("/api/v1/sessions/{id}/stop", runningId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        UUID pendingWorkId = seedWaitingSession(SessionType.WORK);
        mvc.perform(post("/api/v1/sessions/{id}/skip-break", pendingWorkId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_INTERVAL_TYPE"));

        UUID missing = UUID.randomUUID();
        mvc.perform(post("/api/v1/sessions/{id}/intervals/start", missing)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        mvc.perform(post("/api/v1/sessions/{id}/skip-break", missing)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    private UUID seedWaitingSession(SessionType pendingType) {
        Instant now = Instant.now();
        TimeStamp createdAt = new TimeStamp(now.minusSeconds(600));
        SessionSettings settings = new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(60),
                Duration.ofSeconds(120), 4, false, false);
        FocusSession session = FocusSessionFactory.create(settings, createdAt);
        session.start(createdAt);
        session.completeInterval(pendingType == SessionType.WORK
                ? SessionType.SHORT_BREAK : pendingType, new TimeStamp(now.minusSeconds(540)));
        if (pendingType == SessionType.WORK) {
            session.startNextInterval(new TimeStamp(now.minusSeconds(539)));
            session.completeInterval(SessionType.WORK, new TimeStamp(now.minusSeconds(479)));
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> sessions.save(session));
        return session.id().value();
    }
}
