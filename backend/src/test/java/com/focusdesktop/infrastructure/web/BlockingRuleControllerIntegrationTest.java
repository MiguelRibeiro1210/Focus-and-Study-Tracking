package com.focusdesktop.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.domain.repository.BlockingPolicyRepository;
import com.focusdesktop.domain.model.ObservedDistraction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "focusdesktop.data-dir=target/test-blocking-api-data", "focusdesktop.api.token=test-token",
        "spring.datasource.url=jdbc:h2:mem:blocking_api_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
class BlockingRuleControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired BlockingPolicyRepository policies;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.focusdesktop.application.usecase.ManageBlockingRulesUseCase rules;

    @BeforeEach
    void clearRules() { jdbc.update("DELETE FROM blocking_rule"); }

    @Test
    void diagnosticStatusIsAuthenticatedReadOnlyAndDoesNotClaimEnforcement() throws Exception {
        mvc.perform(get("/api/v1/blocking-status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/blocking-status").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monitorEnabled").value(false))
                .andExpect(jsonPath("$.applicationDetection").value("DISABLED"))
                .andExpect(jsonPath("$.applicationEnforcement").value(false))
                .andExpect(jsonPath("$.websiteEnforcement").value(false))
                .andExpect(jsonPath("$.checkedAt").isString());
    }

    @Test
    void migrationSeedsExactlyOneEmptyPolicyAndCrudReloadsTheTypedDomain() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_policy", Integer.class));
        mvc.perform(get("/api/v1/blocking-rules").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        JsonNode created = create("Video", "YouTube.COM.");
        String id = created.get("id").asText();
        assertEquals("youtube.com", created.at("/target/domain").asText());
        assertNotNull(created.get("createdAt"));
        assertTrue(policies.findActive().orElseThrow().evaluate(ObservedDistraction.website("music.youtube.com")).isPresent());
        mvc.perform(patch("/api/v1/blocking-rules/" + id).header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
        assertTrue(policies.findActive().orElseThrow().evaluate(ObservedDistraction.website("youtube.com")).isEmpty());
        mvc.perform(delete("/api/v1/blocking-rules/" + id).header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/blocking-rules/" + id).header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_policy", Integer.class));
    }

    @Test
    void canonicalDuplicatesAndInvalidEditsPreservePreviousConfiguration() throws Exception {
        create("Video", "youtube.com");
        JsonNode other = create("Other", "example.org");
        mvc.perform(post("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content(website("Duplicate", "YOUTUBE.COM.")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RULE"));
        mvc.perform(patch("/api/v1/blocking-rules/" + other.get("id").asText())
                        .header("Authorization", "Bearer test-token").contentType("application/json")
                        .content("{\"displayName\":\"Changed\",\"target\":{\"type\":\"WEBSITE\",\"domain\":\"youtube.com\"}}"))
                .andExpect(status().isConflict());
        assertEquals("Other", jdbc.queryForObject("SELECT display_name FROM blocking_rule WHERE id=?", String.class,
                UUID.fromString(other.get("id").asText())));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
    }

    @Test
    void targetReplacementPersistsApplicationIdentityAndRejectsCanonicalAppDuplicate() throws Exception {
        String id = create("Browser", "example.org").get("id").asText();
        String target = "{\"type\":\"APPLICATION\",\"platform\":\"WINDOWS\",\"matchKind\":\"PROCESS_NAME\",\"value\":\"CHAT.EXE\"}";
        mvc.perform(patch("/api/v1/blocking-rules/" + id).header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"target\":" + target + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.target.value").value("CHAT.EXE"))
                .andExpect(jsonPath("$.target.domain").doesNotExist());
        mvc.perform(post("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"displayName\":\"Duplicate\",\"target\":"
                                + target.replace("CHAT.EXE", "chat.exe") + ",\"action\":\"WARN\",\"category\":\"OTHER\",\"enabled\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/blocking-rules/" + id).header("Authorization", "Bearer test-token")
                        .contentType("application/json").content("{\"target\":" + target.replace("CHAT.EXE", "chat.exe") + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.target.value").value("chat.exe"));
    }

    @Test
    void databaseConstraintsProtectCanonicalTargetsAndTheSingletonPolicy() throws Exception {
        create("Video", "youtube.com");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO blocking_rule SELECT ?,policy_id,display_name,target_type,platform,match_kind,"
                        + "target_value,target_key,category,action,enabled,created_at,updated_at FROM blocking_rule",
                UUID.randomUUID()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO blocking_policy (id,singleton_slot) VALUES (?,1)", UUID.randomUUID()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
    }

    @Test
    void aFailureAfterRuleDeletionRollsBackTheTransaction() throws Exception {
        create("Video", "youtube.com");
        assertThrows(IllegalStateException.class, () ->
                new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            rules.clear();
                            throw new IllegalStateException("Simulated failure after deletion");
                        }));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"enabled\":null}", "{\"enabled\":\"false\"}", "{\"id\":\"other\"}",
            "{\"target\":{\"type\":\"WEBSITE\",\"domain\":\"example.org\",\"value\":\"bad\"}}",
            "{\"target\":{\"type\":\"WEBSITE\",\"domain\":\"https://example.org\"}}", "{\"action\":\"ALLOW\"}"})
    void rejectsInvalidPatchFieldsWithoutMutation(String body) throws Exception {
        String id = create("Video", "youtube.com").get("id").asText();
        mvc.perform(patch("/api/v1/blocking-rules/" + id).header("Authorization", "Bearer test-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").isString());
        assertEquals("youtube.com", jdbc.queryForObject("SELECT target_value FROM blocking_rule", String.class));
    }

    @Test
    void securityPreflightAndMalformedRequestsUseTheDocumentedStatuses() throws Exception {
        mvc.perform(get("/api/v1/blocking-rules")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                .header("Origin", "https://example.org")).andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/blocking-rules/" + UUID.randomUUID()).header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
        for (String body : List.of("[1]", "{")) {
            mvc.perform(post("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                .contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void concurrentDistinctAndDuplicateCreatesDoNotLoseRulesOrReturnServerErrors() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                final String domain = i < 2 ? "same.example" : "site" + i + ".example";
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return mvc.perform(post("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                            .contentType("application/json").content(website(domain, domain))).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(15, TimeUnit.SECONDS));
            assertEquals(3, statuses.stream().filter(code -> code == 201).count());
            assertEquals(1, statuses.stream().filter(code -> code == 409).count());
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_policy", Integer.class));
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test
    void exportsIncludeRulesAndOnlyConfirmedDeleteAllClearsThem() throws Exception {
        create("Video", "youtube.com");
        mvc.perform(get("/api/v1/settings/export?format=json").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blockingRules[0].target.domain").value("youtube.com"));
        byte[] archive = mvc.perform(get("/api/v1/settings/export?format=csv-zip").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        boolean found = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("blocking-rules.csv")) { found = true; assertTrue(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("youtube.com")); }
            }
        }
        assertTrue(found);
        mvc.perform(delete("/api/v1/sessions/history").header("Authorization", "Bearer test-token")).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
        mvc.perform(delete("/api/v1/settings/data").header("Authorization", "Bearer test-token")
                .contentType("application/json").content("{\"confirmation\":\"wrong\"}")).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
        mvc.perform(delete("/api/v1/settings/data").header("Authorization", "Bearer test-token")
                .contentType("application/json").content("{\"confirmation\":\"DELETE_ALL_DATA\"}")).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_policy", Integer.class));
    }

    private JsonNode create(String name, String domain) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/blocking-rules").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(website(name, domain))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String website(String name, String domain) {
        return "{\"displayName\":\"" + name + "\",\"target\":{\"type\":\"WEBSITE\",\"domain\":\"" + domain
                + "\"},\"category\":\"OTHER\",\"action\":\"BLOCK\",\"enabled\":true}";
    }
}
