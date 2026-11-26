package com.focusdesktop.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.dto.*;
import com.focusdesktop.application.port.DistractionLogReader;
import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.readmodel.DistractionLog;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcDistractionLogAdapter implements DistractionLogReader {
    private static final String TYPES = "('DistractionDetected','DistractionBlocked','DistractionBlockFailed')";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final Clock clock;
    public JdbcDistractionLogAdapter(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.transaction = new TransactionTemplate(transactions); this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${focusdesktop.distraction.projection-delay-ms:1000}",
            initialDelayString = "${focusdesktop.distraction.projection-initial-delay-ms:1000}", scheduler = "distractionTaskScheduler")
    public void projectPending() {
        List<UUID> ids = jdbc.query("SELECT event_id FROM domain_event_outbox WHERE delivered_at IS NULL AND event_type IN " + TYPES
                        + " AND (next_attempt_at IS NULL OR next_attempt_at<=?) ORDER BY occurred_at,event_id LIMIT 100",
                (row, index) -> row.getObject(1, UUID.class), utc(clock.instant()));
        for (UUID id : ids) {
            try { project(id); }
            catch (RuntimeException exception) {
                jdbc.update("UPDATE domain_event_outbox SET attempt_count=attempt_count+1,next_attempt_at=?,last_error=? WHERE event_id=? AND delivered_at IS NULL",
                        utc(clock.instant().plusSeconds(10)), "Distraction snapshot projection failed validation; retry pending.", id);
            }
        }
    }
    public void project(UUID eventId) {
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT singleton_slot FROM distraction_projection_lock WHERE singleton_slot=1 FOR UPDATE", Integer.class);
            var source = jdbc.query("SELECT event_type,aggregate_id,payload FROM domain_event_outbox WHERE event_id=? AND event_type IN " + TYPES,
                    (row, index) -> new Source(row.getString(1), row.getObject(2, UUID.class), row.getString(3)), eventId);
            if (source.isEmpty()) return;
            var event = decode(source.get(0).payload());
            var domain = event.toDomainEvent();
            if (!eventId.equals(event.eventId()) || !source.get(0).sessionId().equals(event.sessionId())
                    || !domain.getClass().getSimpleName().equals(source.get(0).type())) throw new IllegalArgumentException("Snapshot metadata mismatch");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM focus_session WHERE id=?", Integer.class, event.sessionId()) == 0) {
                jdbc.update("DELETE FROM domain_event_outbox WHERE event_id=?", eventId); return;
            }
            var existing = jdbc.query("SELECT payload FROM distraction_projection_event WHERE event_id=?", (row, index) -> row.getString(1), eventId);
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO distraction_projection_event(event_id,detection_id,session_id,event_type,occurred_at,payload) VALUES(?,?,?,?,?,?)",
                        eventId, event.detectionId(), event.sessionId(), source.get(0).type(), utc(event.occurredAt()), encode(event));
            } else if (!decode(existing.get(0)).equals(event)) throw new IllegalArgumentException("Event identity was reused");
            rebuild(event.detectionId());
            jdbc.update("UPDATE domain_event_outbox SET delivered_at=?,last_error=NULL,next_attempt_at=NULL WHERE event_id=?", utc(clock.instant()), eventId);
        });
    }
    private void rebuild(UUID detectionId) {
        List<DistractionEventPayload> entries = jdbc.query("SELECT payload FROM distraction_projection_event WHERE detection_id=? ORDER BY occurred_at,event_id",
                (row, index) -> decode(row.getString(1)), detectionId);
        var detection = entries.stream().filter(event -> event.outcome().equals("PENDING")).findFirst();
        if (detection.isEmpty()) return;
        var detected = (DistractionDetected) detection.get().toDomainEvent();
        for (var duplicate : entries.stream().filter(event -> event.outcome().equals("PENDING")).toList()) {
            var another = (DistractionDetected) duplicate.toDomainEvent();
            if (!detected.detectionId().equals(another.detectionId()) || !detected.sessionId().equals(another.sessionId())
                    || !detected.decision().equals(another.decision()) || !detected.observation().equals(another.observation())
                    || !detected.occurredAt().equals(another.occurredAt())) throw new IllegalArgumentException("Detection identity was reused");
        }
        DistractionEventPayload selected = null;
        for (var entry : entries) {
            if (entry.outcome().equals("PENDING")) continue;
            try {
                new DistractionLog(detected, Optional.of((DistractionBlockOutcome) entry.toDomainEvent()));
                selected = entry; break;
            } catch (IllegalArgumentException ignored) { /* Retain incompatible facts in the ledger, never claim their block. */ }
        }
        int updated = jdbc.update("UPDATE distraction_log SET outcome_payload=? WHERE detection_id=?",
                selected == null ? null : encode(selected), detectionId);
        if (updated == 0) jdbc.update("INSERT INTO distraction_log(detection_id,session_id,occurred_at,detection_payload,outcome_payload) VALUES(?,?,?,?,?)",
                detectionId, detected.sessionId().value(), utc(detection.get().occurredAt()), encode(detection.get()), selected == null ? null : encode(selected));
    }
    @Override public DistractionLogPage find(UUID sessionId, Instant fromInclusive, Instant toExclusive, int page, int size) {
        return transaction.execute(status -> {
            var parameters = new ArrayList<Object>(); var filter = new StringBuilder(" WHERE 1=1");
            if (sessionId != null) { filter.append(" AND session_id=?"); parameters.add(sessionId); }
            if (fromInclusive != null) { filter.append(" AND occurred_at>=?"); parameters.add(utc(fromInclusive)); }
            if (toExclusive != null) { filter.append(" AND occurred_at<?"); parameters.add(utc(toExclusive)); }
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM distraction_log" + filter, Long.class, parameters.toArray());
            parameters.add(size); parameters.add((long) page * size);
            var items = jdbc.query("SELECT detection_payload,outcome_payload FROM distraction_log" + filter
                            + " ORDER BY occurred_at DESC,detection_id ASC LIMIT ? OFFSET ?",
                    (row, index) -> DistractionLogDto.from(decode(row.getString(1)), row.getString(2) == null ? null : decode(row.getString(2))), parameters.toArray());
            return new DistractionLogPage(items, total, page, size);
        });
    }
    @Override public List<DistractionLogDto> exportAll() {
        return jdbc.query("SELECT detection_payload,outcome_payload FROM distraction_log ORDER BY occurred_at DESC,detection_id ASC",
                (row, index) -> DistractionLogDto.from(decode(row.getString(1)), row.getString(2) == null ? null : decode(row.getString(2))));
    }
    private DistractionEventPayload decode(String value) {
        try { return mapper.readValue(value, DistractionEventPayload.class); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid distraction snapshot"); }
    }
    private String encode(DistractionEventPayload value) {
        try { return mapper.writeValueAsString(value); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid distraction snapshot"); }
    }
    private static OffsetDateTime utc(Instant instant) { return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC); }
    private record Source(String type, UUID sessionId, String payload) { }
}
