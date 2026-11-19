package com.focusdesktop.infrastructure.web;

import java.time.Instant;
import java.util.UUID;

public record SessionEventEnvelope(UUID eventId, int version, String type,
                                   Instant occurredAt, SessionResponse payload,
                                   String completedIntervalType) {
    public SessionEventEnvelope(UUID eventId, int version, String type,
                                Instant occurredAt, SessionResponse payload) {
        this(eventId, version, type, occurredAt, payload, null);
    }
}
