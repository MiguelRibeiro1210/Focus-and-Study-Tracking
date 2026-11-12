package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.domain.model.SessionId;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        String sessionType,
        String state,
        String intervalType,
        String intervalState,
        int intervalSequence,
        long plannedDurationSeconds,
        long actualDurationSeconds,
        long actualFocusDurationSeconds,
        long remainingSeconds,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt) {

    public static SessionResponse from(SessionDto session) {
        SessionId id = session.sessionId();
        return new SessionResponse(id.value(), session.sessionType().name(), session.state().name(),
                session.intervalType().name(), session.intervalState().name(), session.intervalSequence(),
                session.plannedDuration().seconds(), session.actualDuration().seconds(),
                session.actualFocusDuration().seconds(), session.remainingDuration().seconds(),
                session.createdAt().value(), session.startedAt().value(),
                session.endedAt() == null ? null : session.endedAt().value());
    }
}
