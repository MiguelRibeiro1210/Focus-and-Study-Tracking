package com.focusdesktop.domain.factories.snapshots;

import com.focusdesktop.domain.model.*;

import java.util.List;

/** Persistence-neutral state needed to reconstitute a focus session. */
public record FocusSessionSnapshot(
        SessionId id,
        String projectName,
        SessionType sessionType,
        SessionSettings settings,
        TimeStamp createdAt,
        SessionState state,
        TimeStamp startedAt,
        TimeStamp endedAt,
        List<SessionIntervalSnapshot> intervals) {
    public FocusSessionSnapshot {
        if (projectName == null || projectName.isBlank() || projectName.trim().length() > 120) {
            throw new IllegalArgumentException("projectName must contain between 1 and 120 characters");
        }
        projectName = projectName.trim();
        intervals = List.copyOf(intervals);
    }

    public FocusSessionSnapshot(com.focusdesktop.domain.model.SessionId id,
                                com.focusdesktop.domain.model.SessionType sessionType,
                                com.focusdesktop.domain.model.SessionSettings settings,
                                com.focusdesktop.domain.model.TimeStamp createdAt,
                                com.focusdesktop.domain.model.SessionState state,
                                com.focusdesktop.domain.model.TimeStamp startedAt,
                                com.focusdesktop.domain.model.TimeStamp endedAt,
                                List<SessionIntervalSnapshot> intervals) {
        this(id, "Focus session", sessionType, settings, createdAt, state, startedAt, endedAt, intervals);
    }
}
