package com.focusdesktop.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.focusdesktop.application.port.*;
import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = { "focusdesktop.data-dir=target/test-distraction-log", "focusdesktop.api.token=test-token",
        "spring.datasource.url=jdbc:h2:mem:distraction_log_test;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=",
        "focusdesktop.distraction.projection-initial-delay-ms=86400000", "focusdesktop.session.interval-check-initial-delay-ms=86400000" })
@AutoConfigureMockMvc
class DistractionLogIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FocusSessionRepository sessions;
    @Autowired DomainEventOutbox outbox;
    @Autowired JdbcDistractionLogAdapter projection;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SessionDataMaintenance maintenance;
    @Autowired MockMvc mvc;
    private static final TimeStamp AT = new TimeStamp(Instant.parse("2026-01-01T10:00:00Z"));
    @BeforeEach void clear() { jdbc.update("DELETE FROM domain_event_outbox"); jdbc.update("DELETE FROM focus_session"); }
    private FocusSession session() {
        var session = FocusSessionFactory.create(new SessionSettings(Duration.ofSeconds(600), Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false), AT);
        session.start(AT); new TransactionTemplate(transactions).executeWithoutResult(status -> sessions.save(session)); return session;
    }
    private DistractionDetected detection(FocusSession session, boolean website) {
        DistractionTarget target = website ? new WebsiteTarget("example.com") : new ApplicationTarget(TargetPlatform.LINUX, ApplicationMatchKind.PROCESS_NAME, "chat");
        var decision = new BlockingDecision(BlockingRuleId.create(), target, DistractionCategory.OTHER, BlockingAction.BLOCK);
        var observation = website ? ObservedDistraction.website("music.example.com")
                : ObservedDistraction.application(new AppIdentifier(TargetPlatform.LINUX, "/usr/bin/chat", "chat"));
        return DistractionDetected.capture(session.id(), decision, observation, AT);
    }
    private void append(DomainEvent event) { new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.append(event)); }
    @Test void handlesOutcomeFirstDuplicatesBothTargetTypesAndConfirmedFailure() {
        var session = session(); var app = detection(session, false);
        var blocked = DistractionBlocked.from(app, new TimeStamp(AT.value().plusSeconds(1)));
        append(blocked); projection.project(blocked.eventId()); assertEquals(0, projection.exportAll().size());
        append(app); projection.project(app.eventId());
        assertTrue(projection.exportAll().get(0).blocked()); assertEquals("chat", projection.exportAll().get(0).application().processName());
        append(app);
        assertNotNull(jdbc.queryForObject("SELECT delivered_at FROM domain_event_outbox WHERE event_id=?", Object.class, app.eventId()));
        var reused = new DistractionDetected(app.eventId(), app.detectionId(), app.sessionId(), app.decision(), app.observation(), new TimeStamp(AT.value().plusSeconds(3)));
        assertThrows(RuntimeException.class, () -> append(reused));
        projection.project(app.eventId()); projection.project(blocked.eventId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM distraction_projection_event", Integer.class));
        var website = detection(session, true); append(website); projection.project(website.eventId());
        var failed = DistractionBlockFailed.from(website, "Permission denied", new TimeStamp(AT.value().plusSeconds(2)));
        append(failed); projection.project(failed.eventId());
        var entry = projection.exportAll().stream().filter(item -> item.host() != null).findFirst().orElseThrow();
        assertEquals("FAILED", entry.outcome()); assertFalse(entry.blocked()); assertEquals("Permission denied", entry.failureReason());
        assertEquals("music.example.com", entry.host()); assertEquals(2, projection.find(session.id().value(), null, null, 0, 20).total());
    }
    @Test void rejectsMismatchedAndEarlierOutcomesWithoutClaimingABlock() {
        var detection = detection(session(), true); append(detection); projection.project(detection.eventId());
        var early = new DistractionBlocked(UUID.randomUUID(), detection.detectionId(), detection.sessionId(), detection.decision(), detection.observation(), new TimeStamp(AT.value().minusSeconds(1)));
        append(early); projection.project(early.eventId());
        var otherDecision = new BlockingDecision(BlockingRuleId.create(), new WebsiteTarget("other.example"), DistractionCategory.OTHER, BlockingAction.BLOCK);
        var mismatch = new DistractionBlocked(UUID.randomUUID(), detection.detectionId(), detection.sessionId(), otherDecision, ObservedDistraction.website("other.example"), AT);
        append(mismatch); projection.project(mismatch.eventId());
        assertEquals("PENDING", projection.exportAll().get(0).outcome()); assertFalse(projection.exportAll().get(0).blocked());
    }
    @Test void rollsBackReceiptViewAndDeliveryTogether() {
        var detected = detection(session(), true); append(detected);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            projection.project(detected.eventId()); throw new IllegalStateException("Force rollback");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM distraction_projection_event", Integer.class));
        assertEquals(0, projection.exportAll().size());
        assertNull(jdbc.queryForObject("SELECT delivered_at FROM domain_event_outbox WHERE event_id=?", Object.class, detected.eventId()));
        projection.projectPending(); assertEquals(1, projection.exportAll().size());
    }
    @Test void aMalformedEventCannotStarveValidDetectionsAndOutcomeSelectionIsDeterministic() {
        var session = session(); var detected = detection(session, true);
        UUID malformed = UUID.randomUUID();
        jdbc.update("INSERT INTO domain_event_outbox(event_id,event_type,aggregate_id,occurred_at,payload,attempt_count) VALUES(?,?,?,?,?,0)",
                malformed, "DistractionDetected", session.id().value(), java.time.OffsetDateTime.ofInstant(AT.value().minusSeconds(1), java.time.ZoneOffset.UTC), "{\"version\":99}");
        append(detected); projection.projectPending();
        assertEquals(1, projection.exportAll().size());
        assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM domain_event_outbox WHERE event_id=?", Integer.class, malformed));
        var laterFailure = DistractionBlockFailed.from(detected, "Later failure", new TimeStamp(AT.value().plusSeconds(5)));
        var earlierSuccess = DistractionBlocked.from(detected, new TimeStamp(AT.value().plusSeconds(1)));
        append(laterFailure); projection.project(laterFailure.eventId());
        append(earlierSuccess); projection.project(earlierSuccess.eventId());
        assertTrue(projection.exportAll().get(0).blocked());
        assertEquals(earlierSuccess.occurredAt().value(), projection.exportAll().get(0).outcomeAt());
    }
    @Test void concurrentDeletionWaitsForTheObservationThenRemovesItsSourceWithoutResurrection() throws Exception {
        var detected = detection(session(), true);
        var written = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                outbox.append(detected); written.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timeout"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            assertTrue(written.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var deletion = pool.submit(maintenance::deleteAllSessionData);
            assertThrows(java.util.concurrent.TimeoutException.class, () -> deletion.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown(); writer.get(5, java.util.concurrent.TimeUnit.SECONDS); deletion.get(5, java.util.concurrent.TimeUnit.SECONDS);
            projection.projectPending(); assertTrue(projection.exportAll().isEmpty());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Integer.class));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void supportsAuthenticatedStablePaginationFiltersExportAndSessionCleanup() throws Exception {
        var first = session(); var a = detection(first, true); append(a); projection.project(a.eventId());
        first.stop(new TimeStamp(AT.value().plusSeconds(600)));
        new TransactionTemplate(transactions).executeWithoutResult(status -> sessions.save(first));
        var second = session(); var b = detection(second, false); append(b); projection.project(b.eventId());
        mvc.perform(get("/api/v1/distractions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/distractions?size=1&page=0").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/distractions?sessionId=" + first.id().value() + "&from=2026-01-01&to=2026-01-01").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].host").value("music.example.com"));
        mvc.perform(get("/api/v1/distractions?size=101").header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/distractions?from=2026-02-01&to=2026-01-01").header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/settings/export?format=json").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.distractions.length()").value(2));
        var archive = mvc.perform(get("/api/v1/settings/export?format=csv-zip").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        boolean exported = false;
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            for (var file = zip.getNextEntry(); file != null; file = zip.getNextEntry())
                if (file.getName().equals("distractions.csv")) {
                    assertTrue(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("music.example.com")); exported = true;
                }
        }
        assertTrue(exported);
        assertEquals(1, maintenance.clearExpiredTerminalHistory(AT.value().plusSeconds(600)));
        maintenance.clearTerminalHistory();
        assertEquals(1, projection.exportAll().size()); assertEquals(second.id().value(), projection.exportAll().get(0).sessionId());
        append(a); // An in-flight observation for deleted history must be discarded.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox WHERE aggregate_id=?", Integer.class, first.id().value()));
        mvc.perform(delete("/api/v1/settings/data").header("Authorization", "Bearer test-token").contentType("application/json")
                        .content("{\"confirmation\":\"DELETE_ALL_DATA\"}")).andExpect(status().isNoContent());
        assertEquals(0, projection.exportAll().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM distraction_projection_event", Integer.class));
    }
}
