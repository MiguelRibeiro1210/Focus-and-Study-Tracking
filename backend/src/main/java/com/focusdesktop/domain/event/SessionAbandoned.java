package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record SessionAbandoned(SessionId sessionId, TimeStamp occurredAt, Duration actualFocusDuration,
                               String reason) implements DomainEvent {
    public static final String BELOW_MINIMUM_FOCUS_TIME = "BELOW_MINIMUM_FOCUS_TIME";

    public SessionAbandoned {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actualFocusDuration, "actualFocusDuration");
        Objects.requireNonNull(reason, "reason");
    }
}
