package com.focusdesktop.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.factories.snapshots.FocusSessionSnapshot;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:focus_session_adapter_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(com.focusdesktop.infrastructure.persistence.jpa.JpaFocusSessionRepositoryAdapter.class)
class JpaFocusSessionRepositoryAdapterTest {
    private static final TimeStamp START = new TimeStamp(Instant.parse("2026-01-01T10:00:00Z"));
    private static final SessionSettings SETTINGS = new SessionSettings(Duration.ofSeconds(600),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false);

    @Autowired
    private FocusSessionRepository sessions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void flywayAppliesSessionAndOutboxRetryMigrations() {
        var currentMigration = flyway.info().current();
        assertNotNull(currentMigration);
        assertEquals("2", currentMigration.getVersion().getVersion());
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM focus_session", Long.class));
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM session_interval", Long.class));
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'DOMAIN_EVENT_OUTBOX' AND column_name = 'NEXT_ATTEMPT_AT'", Integer.class));
    }

    @Test
    void roundTripsSettingsPausedStateAndOrderedIntervalsThroughDomainFactory() {
        FocusSession session = FocusSessionFactory.create(SETTINGS, START);
        session.start(START);
        session.completeInterval(SessionType.SHORT_BREAK, at(600));
        session.startNextInterval(at(600));
        session.completeInterval(SessionType.WORK, at(900));
        session.startNextInterval(at(900));
        session.pause(at(1025));

        sessions.save(session);
        entityManager.flush();
        entityManager.clear();

        FocusSession restored = sessions.findById(session.id()).orElseThrow();

        assertEquals(session.id(), restored.id());
        assertEquals(SETTINGS, restored.settings());
        assertEquals(SessionState.PAUSED, restored.state());
        assertEquals(3, restored.intervals().size());
        assertEquals(List.of(SessionType.WORK, SessionType.SHORT_BREAK, SessionType.WORK),
                restored.intervals().stream().map(interval -> interval.type()).toList());
        assertEquals(IntervalState.COMPLETED, restored.intervals().get(0).state());
        assertEquals(IntervalState.COMPLETED, restored.intervals().get(1).state());
        assertEquals(IntervalState.RUNNING, restored.currentInterval().state());
        assertEquals(at(1025), restored.currentInterval().pausedAt());
        assertEquals(475, restored.currentInterval().remainingDuration().seconds());
    }

    @Test
    void findsRunningPausedAndWaitingSessionsAndExcludesTerminalSessions() {
        FocusSession session = FocusSessionFactory.create(SETTINGS, START);
        session.start(START);
        sessions.save(session);
        assertEquals(SessionState.RUNNING, sessions.findActive().orElseThrow().state());

        session.pause(at(60));
        sessions.save(session);
        assertEquals(SessionState.PAUSED, sessions.findActive().orElseThrow().state());

        session.resume(at(120));
        session.completeInterval(SessionType.SHORT_BREAK, at(660));
        sessions.save(session);
        assertEquals(SessionState.WAITING_FOR_NEXT_INTERVAL, sessions.findActive().orElseThrow().state());

        session.stop(at(661));
        sessions.save(session);
        assertFalse(sessions.findActive().isPresent());
    }

    @Test
    void updatesVersionWhenAggregateStateChanges() {
        FocusSession session = FocusSessionFactory.create(SETTINGS, START);
        session.start(START);
        sessions.save(session);
        entityManager.flush();
        long initialVersion = jdbc.queryForObject(
                "SELECT version FROM focus_session WHERE id = ?", Long.class, session.id().value());

        session.pause(at(60));
        sessions.save(session);
        entityManager.flush();

        long updatedVersion = jdbc.queryForObject(
                "SELECT version FROM focus_session WHERE id = ?", Long.class, session.id().value());
        assertTrue(updatedVersion > initialVersion);
    }

    @Test
    void rejectsAStaleSessionEntityUsingOptimisticLocking() {
        FocusSession session = FocusSessionFactory.create(SETTINGS, START);
        session.start(START);
        sessions.save(session);
        entityManager.flush();
        UUID sessionId = session.id().value();

        FocusSessionJpaEntity staleCopy = entityManager.find(FocusSessionJpaEntity.class, sessionId);
        entityManager.detach(staleCopy);

        session.pause(at(60));
        FocusSessionJpaEntity latestCopy = entityManager.find(FocusSessionJpaEntity.class, sessionId);
        FocusSessionSnapshot pausedSnapshot = session.snapshot();
        latestCopy.apply(pausedSnapshot, PersistenceJson.write(SETTINGS));
        entityManager.flush();

        assertThrows(PersistenceException.class, () -> {
            entityManager.merge(staleCopy);
            entityManager.flush();
        });
    }

    @Test
    void databaseRejectsTwoActiveSessions() {
        FocusSession first = FocusSessionFactory.create(SETTINGS, START);
        first.start(START);
        FocusSession second = FocusSessionFactory.create(SETTINGS, START);
        second.start(START);
        sessions.save(first);
        sessions.save(second);

        assertThrows(PersistenceException.class, entityManager::flush);
    }

    private static TimeStamp at(long secondsAfterStart) {
        return new TimeStamp(START.value().plusSeconds(secondsAfterStart));
    }
}
