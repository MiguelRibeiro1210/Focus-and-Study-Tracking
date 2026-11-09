package com.focusdesktop.domain.factories;

import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;
import com.focusdesktop.domain.model.*;

import java.util.Objects;

public final class SessionIntervalFactory {
    private SessionIntervalFactory() { }

    public static SessionInterval createPending(int sequenceNumber, SessionType type, Duration duration) {
        return new SessionInterval(IntervalId.generate(), sequenceNumber, type,
                duration, Duration.ofSeconds(0), duration, IntervalState.PENDING,
                null, null, null);
    }

    public static SessionInterval reconstitute(SessionIntervalSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(snapshot.id(), "snapshot.id");
        Objects.requireNonNull(snapshot.type(), "snapshot.type");
        Objects.requireNonNull(snapshot.plannedDuration(), "snapshot.plannedDuration");
        Objects.requireNonNull(snapshot.actualDuration(), "snapshot.actualDuration");
        Objects.requireNonNull(snapshot.remainingDuration(), "snapshot.remainingDuration");
        Objects.requireNonNull(snapshot.state(), "snapshot.state");
        validate(snapshot);
        return new SessionInterval(snapshot.id(), snapshot.sequenceNumber(), snapshot.type(),
                snapshot.plannedDuration(), snapshot.actualDuration(), snapshot.remainingDuration(),
                snapshot.state(), snapshot.startedAt(), snapshot.endedAt(), snapshot.pausedAt());
    }

    private static void validate(SessionIntervalSnapshot s) {
        if (s.sequenceNumber() < 1 || s.plannedDuration().seconds() == 0) {
            throw new IllegalArgumentException("Interval sequence and planned duration must be positive");
        }
        if (s.remainingDuration().seconds() > s.plannedDuration().seconds()) {
            throw new IllegalArgumentException("remainingDuration cannot exceed plannedDuration");
        }
        switch (s.state()) {
            case PENDING -> {
                if (s.actualDuration().seconds() != 0
                        || s.remainingDuration().seconds() != s.plannedDuration().seconds()
                        || s.startedAt() != null || s.endedAt() != null || s.pausedAt() != null) {
                    throw new IllegalArgumentException("Pending interval cannot have timestamps");
                }
            }
            case RUNNING -> {
                if (s.startedAt() == null || s.endedAt() != null
                        || (s.pausedAt() != null && s.pausedAt().compareTo(s.startedAt()) < 0)) {
                    throw new IllegalArgumentException("Running interval must be active or paused, and not ended");
                }
            }
            case COMPLETED -> {
                if (s.endedAt() == null || s.startedAt() == null || s.pausedAt() != null
                        || s.endedAt().compareTo(s.startedAt()) < 0) {
                    throw new IllegalArgumentException("Completed interval must have valid start and end timestamps");
                }
            }
            case SKIPPED -> {
                if (s.type() == SessionType.WORK || s.endedAt() == null
                        || s.actualDuration().seconds() != 0 || s.remainingDuration().seconds() != 0
                        || s.startedAt() != null || s.pausedAt() != null) {
                    throw new IllegalArgumentException("Only an unstarted break interval can be skipped");
                }
            }
        }
    }
}
