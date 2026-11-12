package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record IntervalStarted(SessionId sessionId, SessionType intervalType,
                              SessionState resultingSessionState, Duration remainingDuration,
                              TimeStamp occurredAt) implements DomainEvent {
    public IntervalStarted {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(intervalType, "intervalType");
        Objects.requireNonNull(resultingSessionState, "resultingSessionState");
        Objects.requireNonNull(remainingDuration, "remainingDuration");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
