package com.focusdesktop.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOutboxDeliveryStore implements OutboxDeliveryStore {
    private final SpringDataOutboxEventRepository events;

    public JpaOutboxDeliveryStore(SpringDataOutboxEventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingOutboxEvent> findDue(Instant now, int limit) {
        return events.findDue(now, PageRequest.of(0, limit)).stream()
                .map(event -> new PendingOutboxEvent(event.eventId(), event.eventType(), event.aggregateId(),
                        event.occurredAt(), event.payload(), event.attemptCount()))
                .toList();
    }

    @Override
    @Transactional
    public void markDelivered(UUID eventId, Instant deliveredAt) {
        events.findById(eventId).ifPresent(event -> event.markDelivered(deliveredAt));
    }

    @Override
    @Transactional
    public void recordFailure(UUID eventId, Instant retryAt, String error) {
        events.findById(eventId).ifPresent(event -> event.recordFailure(retryAt, error));
    }
}
