package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record BreakSkipped(SessionId sessionId, SessionType skippedBreakType,
                           TimeStamp occurredAt) implements DomainEvent {
    public BreakSkipped {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(skippedBreakType, "skippedBreakType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
