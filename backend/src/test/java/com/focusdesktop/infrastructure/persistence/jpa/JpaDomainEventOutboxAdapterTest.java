package com.focusdesktop.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.SessionPausedOrResumed;
import com.focusdesktop.domain.event.SessionStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import jakarta.persistence.EntityManager;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:focus_outbox_adapter_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({JpaFocusSessionRepositoryAdapter.class, JpaDomainEventOutboxAdapter.class,
        JpaOutboxDeliveryStore.class})
class JpaDomainEventOutboxAdapterTest {
    private static final TimeStamp START = new TimeStamp(Instant.parse("2026-01-01T10:00:00Z"));
    private static final SessionSettings SETTINGS = new SessionSettings(Duration.ofSeconds(600),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false);

    @Autowired
    private FocusSessionRepository sessions;

    @Autowired
    private DomainEventOutbox outbox;

    @Autowired
    private OutboxDeliveryStore deliveryStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void storesEventsPendingWithStableTypesAndPayloads() {
        FocusSession session = startedSession();
        sessions.save(session);
        outbox.append(new SessionStarted(session.id(), SessionType.WORK, START));
        outbox.append(new SessionPausedOrResumed(session.id(), SessionState.PAUSED, at(60),
                Duration.ofSeconds(540)));
        entityManager.flush();

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_event_outbox WHERE event_type = 'SessionStarted' "
                        + "AND aggregate_id = ? AND delivered_at IS NULL AND attempt_count = 0",
                Integer.class, session.id().value()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_event_outbox WHERE event_type = 'SessionPaused' "
                        + "AND payload LIKE '%PAUSED%'",
                Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_event_outbox WHERE delivered_at IS NOT NULL", Integer.class));
    }

    @Test
    void sessionAndOutboxAppendRollbackTogether() {
        TransactionTemplate independentTransaction = new TransactionTemplate(transactionManager);
        independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(IllegalStateException.class, () -> independentTransaction.execute(status -> {
            FocusSession session = startedSession();
            sessions.save(session);
            outbox.append(new SessionStarted(session.id(), SessionType.WORK, START));
            throw new IllegalStateException("Force transaction rollback");
        }));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM focus_session", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Integer.class));
    }

    @Test
    void rejectsOutboxAppendOutsideAnExistingTransaction() {
        SessionStarted event = new SessionStarted(startedSession().id(), SessionType.WORK, START);
        TransactionTemplate nonTransactional = new TransactionTemplate(transactionManager);
        nonTransactional.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

        assertThrows(RuntimeException.class, () -> nonTransactional.execute(status -> {
            outbox.append(event);
            return null;
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox", Integer.class));
    }

    @Test
    void retriesPendingEventsAndMarksSuccessfulDelivery() {
        FocusSession session = startedSession();
        sessions.save(session);
        outbox.append(new SessionStarted(session.id(), SessionType.WORK, START));
        entityManager.flush();
        UUID eventId = jdbc.queryForObject("SELECT event_id FROM domain_event_outbox", UUID.class);

        var due = deliveryStore.findDue(START.value(), 10);
        assertEquals(1, due.size());
        assertEquals(eventId, due.get(0).eventId());

        Instant retryAt = START.value().plusSeconds(2);
        deliveryStore.recordFailure(eventId, retryAt, "temporary failure");
        entityManager.flush();
        assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM domain_event_outbox WHERE event_id = ?",
                Integer.class, eventId));
        assertEquals("temporary failure", jdbc.queryForObject(
                "SELECT last_error FROM domain_event_outbox WHERE event_id = ?", String.class, eventId));
        assertEquals(0, deliveryStore.findDue(START.value().plusSeconds(1), 10).size());
        assertEquals(1, deliveryStore.findDue(retryAt, 10).size());

        deliveryStore.markDelivered(eventId, retryAt);
        entityManager.flush();
        assertEquals(0, deliveryStore.findDue(retryAt, 10).size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM domain_event_outbox "
                + "WHERE event_id = ? AND delivered_at = ? AND last_error IS NULL",
                Integer.class, eventId, retryAt));
    }

    private static FocusSession startedSession() {
        FocusSession session = FocusSessionFactory.create(SETTINGS, START);
        session.start(START);
        return session;
    }

    private static TimeStamp at(long secondsAfterStart) {
        return new TimeStamp(START.value().plusSeconds(secondsAfterStart));
    }
}
