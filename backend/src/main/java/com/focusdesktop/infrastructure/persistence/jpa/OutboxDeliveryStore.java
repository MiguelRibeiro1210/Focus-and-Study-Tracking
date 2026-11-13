package com.focusdesktop.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxDeliveryStore {
    List<PendingOutboxEvent> findDue(Instant now, int limit);

    void markDelivered(UUID eventId, Instant deliveredAt);

    void recordFailure(UUID eventId, Instant retryAt, String error);
}
