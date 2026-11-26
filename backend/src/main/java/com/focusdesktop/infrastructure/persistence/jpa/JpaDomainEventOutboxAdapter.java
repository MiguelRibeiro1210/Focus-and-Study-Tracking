package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.DomainEvent;
import com.focusdesktop.domain.event.BreakSkipped;
import com.focusdesktop.domain.event.IntervalCompleted;
import com.focusdesktop.domain.event.IntervalStarted;
import com.focusdesktop.domain.event.SessionAbandoned;
import com.focusdesktop.domain.event.SessionCompleted;
import com.focusdesktop.domain.event.SessionPausedOrResumed;
import com.focusdesktop.domain.event.SessionStarted;
import com.focusdesktop.domain.model.SessionState;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaDomainEventOutboxAdapter implements DomainEventOutbox {
    private final SpringDataOutboxEventRepository outbox;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManager entityManager;

    public JpaDomainEventOutboxAdapter(SpringDataOutboxEventRepository outbox, org.springframework.jdbc.core.JdbcTemplate jdbc,
                                      jakarta.persistence.EntityManager entityManager) {
        this.outbox = outbox;
        this.jdbc = jdbc; this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        if (event instanceof com.focusdesktop.domain.event.DistractionDetected
                || event instanceof com.focusdesktop.domain.event.DistractionBlockOutcome) {
            var payload = com.focusdesktop.application.dto.DistractionEventPayload.from(event);
            entityManager.flush();
            if (jdbc.query("SELECT id FROM focus_session WHERE id=? FOR UPDATE", (row, index) -> row.getObject(1, UUID.class), payload.sessionId()).isEmpty())
                return; // A delayed observation must not recreate deleted session history.
            var previous = outbox.findById(payload.eventId());
            if (previous.isPresent()) {
                if (!previous.get().eventType().equals(event.getClass().getSimpleName())
                        || !PersistenceJson.read(previous.get().payload(), com.focusdesktop.application.dto.DistractionEventPayload.class).equals(payload))
                    throw new IllegalArgumentException("Distraction event identity cannot be reused with different facts");
                return; // A duplicate append must not overwrite snapshots or reset delivery.
            }
            outbox.save(new OutboxEventJpaEntity(payload.eventId(), event.getClass().getSimpleName(), payload.sessionId(),
                    event.occurredAt().value(), PersistenceJson.write(payload)));
            return;
        }
        EventDescriptor descriptor = describe(event);
        outbox.save(new OutboxEventJpaEntity(UUID.randomUUID(), descriptor.type(), descriptor.aggregateId(),
                event.occurredAt().value(), PersistenceJson.write(event)));
    }

    private static EventDescriptor describe(DomainEvent event) {
        if (event instanceof IntervalCompleted completed) {
            return new EventDescriptor("IntervalCompleted", completed.sessionId().value());
        }
        if (event instanceof IntervalStarted started) {
            return new EventDescriptor("IntervalStarted", started.sessionId().value());
        }
        if (event instanceof BreakSkipped skipped) {
            return new EventDescriptor("BreakSkipped", skipped.sessionId().value());
        }
        if (event instanceof SessionStarted started) {
            return new EventDescriptor("SessionStarted", started.sessionId().value());
        }
        if (event instanceof SessionPausedOrResumed stateChanged) {
            String type = stateChanged.resultingState() == SessionState.PAUSED
                    ? "SessionPaused" : "SessionResumed";
            return new EventDescriptor(type, stateChanged.sessionId().value());
        }
        if (event instanceof SessionCompleted completed) {
            return new EventDescriptor("SessionCompleted", completed.sessionId().value());
        }
        if (event instanceof SessionAbandoned abandoned) {
            return new EventDescriptor("SessionAbandoned", abandoned.sessionId().value());
        }
        throw new IllegalArgumentException("Unsupported domain event type: " + event.getClass().getName());
    }

    private record EventDescriptor(String type, UUID aggregateId) { }
}
