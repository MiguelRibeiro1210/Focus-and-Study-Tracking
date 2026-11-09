package com.focusdesktop.domain.factories.snapshots;

import com.focusdesktop.domain.model.*;

/** Persistence-neutral state needed to reconstitute a session interval. */
public record SessionIntervalSnapshot(
        IntervalId id,
        int sequenceNumber,
        SessionType type,
        Duration plannedDuration,
        Duration actualDuration,
        Duration remainingDuration,
        IntervalState state,
        TimeStamp startedAt,
        TimeStamp endedAt,
        TimeStamp pausedAt) {
}
