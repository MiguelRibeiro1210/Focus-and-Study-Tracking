package com.focusdesktop.application.dto;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;

public record SessionDto(
        SessionId sessionId,
        SessionType sessionType,
        SessionState state,
        SessionType intervalType,
        IntervalState intervalState,
        int intervalSequence,
        Duration plannedDuration,
        Duration actualDuration,
        Duration actualFocusDuration,
        Duration remainingDuration,
        TimeStamp createdAt,
        TimeStamp startedAt,
        TimeStamp endedAt) {

    public SessionDto {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(sessionType, "sessionType");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(intervalType, "intervalType");
        Objects.requireNonNull(intervalState, "intervalState");
        Objects.requireNonNull(plannedDuration, "plannedDuration");
        Objects.requireNonNull(actualDuration, "actualDuration");
        Objects.requireNonNull(actualFocusDuration, "actualFocusDuration");
        Objects.requireNonNull(remainingDuration, "remainingDuration");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(startedAt, "startedAt");
    }

    public static SessionDto from(FocusSession session, TimeStamp asOf) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(asOf, "asOf");
        var interval = session.currentInterval();
        return new SessionDto(session.id(), session.sessionType(), session.state(), interval.type(),
                interval.state(), interval.sequenceNumber(), interval.plannedDuration(),
                interval.actualDuration(asOf), session.actualFocusDuration(asOf), interval.remainingDuration(asOf),
                session.createdAt(), session.startedAt(), session.endedAt());
    }
}
