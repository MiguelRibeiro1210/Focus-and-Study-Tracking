package com.focusdesktop.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

public record PendingOutboxEvent(UUID eventId, String eventType, UUID aggregateId,
                                 Instant occurredAt, String payload, int attemptCount) { }
