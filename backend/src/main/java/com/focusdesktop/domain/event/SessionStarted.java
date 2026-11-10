package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record SessionStarted(SessionId sessionId, SessionType sessionType,
                             TimeStamp occurredAt) implements DomainEvent {
    public SessionStarted {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(sessionType, "sessionType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
