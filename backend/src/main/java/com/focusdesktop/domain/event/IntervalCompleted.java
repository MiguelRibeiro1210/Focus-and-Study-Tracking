package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record IntervalCompleted(SessionId sessionId, SessionType completedIntervalType,
                                SessionType nextIntervalType, SessionState resultingSessionState,
                                Duration nextIntervalRemainingDuration,
                                TimeStamp occurredAt) implements DomainEvent {
    public IntervalCompleted {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(completedIntervalType, "completedIntervalType");
        Objects.requireNonNull(nextIntervalType, "nextIntervalType");
        Objects.requireNonNull(resultingSessionState, "resultingSessionState");
        Objects.requireNonNull(nextIntervalRemainingDuration, "nextIntervalRemainingDuration");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
