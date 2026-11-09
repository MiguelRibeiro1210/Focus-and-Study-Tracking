package com.focusdesktop.domain.factories.snapshots;

import com.focusdesktop.domain.model.*;

import java.util.List;

/** Persistence-neutral state needed to reconstitute a focus session. */
public record FocusSessionSnapshot(
        SessionId id,
        SessionType sessionType,
        SessionSettings settings,
        TimeStamp createdAt,
        SessionState state,
        TimeStamp startedAt,
        TimeStamp endedAt,
        List<SessionIntervalSnapshot> intervals) {
    public FocusSessionSnapshot {
        intervals = List.copyOf(intervals);
    }
}
