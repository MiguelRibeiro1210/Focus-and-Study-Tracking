package com.focusdesktop.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.SessionStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "focusdesktop.data-dir=target/test-settings-api-data",
        "focusdesktop.api.token=test-token",
        "spring.datasource.url=jdbc:h2:mem:settings_api_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class SettingsControllerIntegrationTest {
    private static final SessionSettings SESSION_SETTINGS = new SessionSettings(Duration.ofSeconds(25 * 60L),
            Duration.ofSeconds(5 * 60L), Duration.ofSeconds(15 * 60L), 4, false, false);
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private FocusSessionRepository sessions;
    @Autowired private DomainEventOutbox outbox;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM domain_event_outbox");
        jdbc.update("DELETE FROM session_interval");
        jdbc.update("DELETE FROM focus_session");
        jdbc.update("DELETE FROM timer_profile");
        jdbc.update("DELETE FROM app_settings");
    }

    @Test
    void servesSeededSettingsAndSupportsProfileCrudAndDefaultSelection() throws Exception {
        String response = mvc.perform(get("/api/v1/settings").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyGoalHours").value(3))
                .andExpect(jsonPath("$.theme").value("LIGHT"))
                .andExpect(jsonPath("$.profiles.length()").value(3))
                .andExpect(jsonPath("$.profiles[0].name").value("Classic"))
                .andReturn().getResponse().getContentAsString();
        JsonNode settings = mapper.readTree(response);
        UUID defaultId = UUID.fromString(settings.get("defaultProfileId").asText());
        UUID otherId = UUID.fromString(settings.get("profiles").get(1).get("id").asText());

        mvc.perform(post("/api/v1/settings/profiles").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"name\":\"Exam routine\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profiles.length()").value(4));

        mvc.perform(put("/api/v1/settings/profiles/{id}", defaultId)
                        .header("Authorization", "Bearer test-token").contentType("application/json")
                        .content("{\"name\":\"Classic edited\",\"workSeconds\":1800,"
                                + "\"shortBreakSeconds\":300,\"longBreakSeconds\":900,"
                                + "\"intervalsBeforeLongBreak\":4,\"autoStartBreaks\":true,\"autoStartWork\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[0].name").value("Classic edited"));

        mvc.perform(put("/api/v1/settings/profiles/default").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"profileId\":\"" + otherId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProfileId").value(otherId.toString()));

        mvc.perform(delete("/api/v1/settings/profiles/{id}", defaultId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsProfileNameCollisionsDefaultDeletionInvalidTimerAndInvalidGoal() throws Exception {
        String response = mvc.perform(get("/api/v1/settings").header("Authorization", "Bearer test-token"))
                .andReturn().getResponse().getContentAsString();
        UUID defaultId = UUID.fromString(mapper.readTree(response).get("defaultProfileId").asText());

        mvc.perform(post("/api/v1/settings/profiles").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"name\":\" classic \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/settings/profiles/{id}", defaultId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SETTINGS_CONFLICT"));
        mvc.perform(put("/api/v1/settings/profiles/{id}", defaultId)
                        .header("Authorization", "Bearer test-token").contentType("application/json")
                        .content("{\"name\":\"Bad profile\",\"workSeconds\":30,"
                                + "\"shortBreakSeconds\":300,\"longBreakSeconds\":600,"
                                + "\"intervalsBeforeLongBreak\":4,\"autoStartBreaks\":false,\"autoStartWork\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/settings/application").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"dailyGoalHours\":25,\"theme\":\"DARK\","
                                + "\"notificationsEnabled\":true,\"retentionDays\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savesApplicationPreferencesAndAppliesPartialOverrideToOneSessionOnly() throws Exception {
        mvc.perform(put("/api/v1/settings/application").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"dailyGoalHours\":6,\"theme\":\"DARK\","
                                + "\"notificationsEnabled\":false,\"retentionDays\":180}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyGoalHours").value(6))
                .andExpect(jsonPath("$.retentionDays").value(180));

        mvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"sessionType\":\"WORK\","
                                + "\"projectName\":\"One-time timer\",\"timerOverride\":{"
                                + "\"workSeconds\":120,\"shortBreakSeconds\":240}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.remainingSeconds").value(120));

        FocusSession session = sessions.findActive().orElseThrow();
        assertEquals(120, session.settings().workDuration().seconds());
        assertEquals(240, session.settings().shortBreakDuration().seconds());
        assertEquals(15 * 60, session.settings().longBreakDuration().seconds());
        assertEquals(4, session.settings().intervalsBeforeLongBreak());
        assertEquals(false, session.settings().autoStartBreaks());

        mvc.perform(get("/api/v1/settings").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profiles[0].workSeconds").value(25 * 60));
    }

    @Test
    void clearHistoryPreservesSettingsAndActiveSessionAndDeleteAllReseedsDefaults() throws Exception {
        mvc.perform(get("/api/v1/settings").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        FocusSession completed = saveSession("completed", true);
        FocusSession active = saveSession("active", false);
        assertNotNull(completed.id());

        mvc.perform(delete("/api/v1/sessions/history").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM focus_session", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Integer.class));
        assertEquals(active.id().value(), sessions.findActive().orElseThrow().id().value());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM timer_profile", Integer.class));

        mvc.perform(delete("/api/v1/settings/data").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"confirmation\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/settings/data").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"confirmation\":\"DELETE_ALL_DATA\"}"))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM focus_session", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_settings", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM timer_profile", Integer.class));
    }

    @Test
    void exportsJsonAndCsvZipWithExpectedDataFiles() throws Exception {
        saveSession("completed for export", true);
        mvc.perform(get("/api/v1/settings/export?format=json").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.profiles.length()").value(3))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].intervals.length()").value(1));

        byte[] zip = mvc.perform(get("/api/v1/settings/export?format=csv-zip")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.Map<String, String> files = new java.util.HashMap<>();
            for (var entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                files.put(entry.getName(), new String(entries.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            java.util.Set<String> names = files.keySet();
            assertTrue(names.containsAll(java.util.Set.of("settings.csv", "profiles.csv", "sessions.csv",
                    "intervals.csv", "analytics.csv")));
            assertTrue(files.get("sessions.csv").contains("completed for export"));
            assertTrue(files.get("intervals.csv").contains("WORK"));
            assertTrue(files.get("analytics.csv").contains("TOTAL"));
        }
    }

    private FocusSession saveSession(String name, boolean terminal) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            TimeStamp started = new TimeStamp(START.plusSeconds(name.equals("active") ? 0 : 10));
            FocusSession session = FocusSessionFactory.create(name, SESSION_SETTINGS, started);
            session.start(started);
            if (terminal) session.stop(new TimeStamp(started.value().plusSeconds(6 * 60L)));
            sessions.save(session);
            outbox.append(new SessionStarted(session.id(), SessionType.WORK, started));
            return session;
        });
    }
}
