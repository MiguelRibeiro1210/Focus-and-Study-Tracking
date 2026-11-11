package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.DomainEvent;
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

    public JpaDomainEventOutboxAdapter(SpringDataOutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        EventDescriptor descriptor = describe(event);
        outbox.save(new OutboxEventJpaEntity(UUID.randomUUID(), descriptor.type(), descriptor.aggregateId(),
                event.occurredAt().value(), PersistenceJson.write(event)));
    }

    private static EventDescriptor describe(DomainEvent event) {
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
