package com.focusdesktop.domain.factories;

import com.focusdesktop.domain.factories.snapshots.FocusSessionSnapshot;
import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;
import com.focusdesktop.domain.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Creates sessions and validates their persisted state before reconstitution. */
public final class FocusSessionFactory {
    private FocusSessionFactory() { }

    public static FocusSession create(SessionSettings settings, TimeStamp now) {
        return new FocusSession(SessionId.generate(), SessionType.WORK,
                Objects.requireNonNull(settings, "settings"), Objects.requireNonNull(now, "now"),
                SessionState.IDLE, null, null, new ArrayList<>());
    }

    public static FocusSession reconstitute(FocusSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(snapshot.id(), "snapshot.id");
        Objects.requireNonNull(snapshot.sessionType(), "snapshot.sessionType");
        Objects.requireNonNull(snapshot.settings(), "snapshot.settings");
        Objects.requireNonNull(snapshot.createdAt(), "snapshot.createdAt");
        Objects.requireNonNull(snapshot.state(), "snapshot.state");
        if (snapshot.sessionType() != SessionType.WORK) {
            throw new IllegalArgumentException("A focus session must start with a work interval");
        }
        ArrayList<SessionInterval> intervals = new ArrayList<>();
        for (SessionIntervalSnapshot interval : snapshot.intervals()) {
            intervals.add(SessionIntervalFactory.reconstitute(interval));
        }
        validate(snapshot, intervals);
        return new FocusSession(snapshot.id(), snapshot.sessionType(), snapshot.settings(),
                snapshot.createdAt(), snapshot.state(), snapshot.startedAt(), snapshot.endedAt(), intervals);
    }

    private static void validate(FocusSessionSnapshot s, java.util.List<SessionInterval> intervals) {
        Set<IntervalId> ids = new HashSet<>();
        int activeIntervals = 0;
        for (int i = 0; i < intervals.size(); i++) {
            SessionInterval interval = intervals.get(i);
            if (!ids.add(interval.id()) || interval.sequenceNumber() != i + 1) {
                throw new IllegalArgumentException("Intervals must have unique IDs and contiguous sequence numbers");
            }
            if ((i == 0 && interval.type() != SessionType.WORK)
                    || (i > 0 && !follows(intervals.get(i - 1).type(), interval.type()))) {
                throw new IllegalArgumentException("Work and break intervals must alternate, starting with work");
            }
            if (interval.state() == IntervalState.RUNNING) activeIntervals++;
            if (i < intervals.size() - 1
                    && interval.state() != IntervalState.COMPLETED
                    && interval.state() != IntervalState.SKIPPED) {
                throw new IllegalArgumentException("Only the last interval may be active or pending");
            }
        }
        if (activeIntervals > 1) {
            throw new IllegalArgumentException("A session can have at most one running interval");
        }
        if (s.state() == SessionState.IDLE) {
            if (!intervals.isEmpty() || s.startedAt() != null || s.endedAt() != null) {
                throw new IllegalArgumentException("Idle session cannot have intervals or lifecycle timestamps");
            }
            return;
        }
        if (s.startedAt() == null || s.startedAt().compareTo(s.createdAt()) < 0) {
            throw new IllegalArgumentException("Started session must have a valid startedAt");
        }
        if (intervals.isEmpty()) {
            throw new IllegalArgumentException("Started session must contain intervals");
        }
        if (s.state().isTerminal()) {
            if (s.endedAt() == null || s.endedAt().compareTo(s.startedAt()) < 0 || activeIntervals != 0) {
                throw new IllegalArgumentException("Terminal session must have a valid endedAt");
            }
            return;
        }
        if (s.endedAt() != null) {
            throw new IllegalArgumentException("Active session cannot have endedAt");
        }
        SessionInterval current = intervals.get(intervals.size() - 1);
        boolean consistent = switch (s.state()) {
            case RUNNING -> activeIntervals == 1 && current.state() == IntervalState.RUNNING && !current.isPaused();
            case PAUSED -> activeIntervals == 1 && current.state() == IntervalState.RUNNING && current.isPaused();
            case WAITING_FOR_NEXT_INTERVAL -> activeIntervals == 0 && current.state() == IntervalState.PENDING;
            default -> false;
        };
        if (!consistent) {
            throw new IllegalArgumentException("Session state does not match its current interval");
        }
    }

    private static boolean follows(SessionType previous, SessionType next) {
        return previous == SessionType.WORK
                ? next == SessionType.SHORT_BREAK || next == SessionType.LONG_BREAK
                : next == SessionType.WORK;
    }
}
