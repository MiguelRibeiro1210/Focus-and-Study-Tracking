package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record SessionPausedOrResumed(SessionId sessionId, TimeStamp occurredAt, Duration remainingDuration)
        implements DomainEvent {
    public SessionPausedOrResumed {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(remainingDuration, "remainingDuration");
    }
}
