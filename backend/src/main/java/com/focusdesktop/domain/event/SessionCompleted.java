package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record SessionCompleted(SessionId sessionId, TimeStamp occurredAt, Duration actualFocusDuration)
        implements DomainEvent {
    public SessionCompleted {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actualFocusDuration, "actualFocusDuration");
    }
}
