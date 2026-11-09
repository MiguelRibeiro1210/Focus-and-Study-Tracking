package com.focusdesktop.domain.factories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.IntervalId;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionInterval;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionIntervalFactoryTest {
    private static final TimeStamp T0 = at(0);
    private static final TimeStamp T1 = at(60);

    @Test
    void createsIndependentPendingIntervalsWithFullRemainingTime() {
        SessionInterval first = SessionIntervalFactory.createPending(1, SessionType.WORK, seconds(60));
        SessionInterval second = SessionIntervalFactory.createPending(2, SessionType.SHORT_BREAK, seconds(60));

        assertNotEquals(first.id(), second.id());
        assertEquals(1, first.sequenceNumber());
        assertEquals(SessionType.WORK, first.type());
        assertEquals(IntervalState.PENDING, first.state());
        assertEquals(0, first.actualDuration().seconds());
        assertEquals(60, first.remainingDuration().seconds());
        assertNull(first.startedAt());
        assertNull(first.endedAt());
    }

    @Test
    void reconstitutesEveryPersistableIntervalState() {
        List<SessionIntervalSnapshot> snapshots = List.of(
                snapshot(1, SessionType.WORK, IntervalState.PENDING, 0, 60, null, null, null),
                snapshot(1, SessionType.WORK, IntervalState.RUNNING, 0, 60, T0, null, null),
                snapshot(1, SessionType.WORK, IntervalState.RUNNING, 30, 30, T0, null, T1),
                snapshot(1, SessionType.WORK, IntervalState.COMPLETED, 60, 0, T0, T1, null),
                snapshot(1, SessionType.SHORT_BREAK, IntervalState.SKIPPED, 0, 0, null, T0, null));

        List<SessionInterval> intervals = snapshots.stream()
                .map(SessionIntervalFactory::reconstitute)
                .toList();

        assertEquals(List.of(IntervalState.PENDING, IntervalState.RUNNING, IntervalState.RUNNING,
                        IntervalState.COMPLETED, IntervalState.SKIPPED),
                intervals.stream().map(SessionInterval::state).toList());
        assertFalse(intervals.get(1).isPaused());
        assertTrue(intervals.get(2).isPaused());
        assertEquals(30, intervals.get(2).actualDuration().seconds());
        assertEquals(0, intervals.get(4).remainingDuration().seconds());
    }

    @Test
    void rejectsMissingSnapshotAndRequiredFields() {
        assertThrows(NullPointerException.class, () -> SessionIntervalFactory.reconstitute(null));
        assertThrows(NullPointerException.class, () -> SessionIntervalFactory.reconstitute(
                new SessionIntervalSnapshot(null, 1, SessionType.WORK, seconds(60), seconds(0), seconds(60),
                        IntervalState.PENDING, null, null, null)));
    }

    @Test
    void rejectsNonPositiveSequenceAndPlannedDuration() {
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.createPending(
                0, SessionType.WORK, seconds(60)));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.createPending(
                1, SessionType.WORK, seconds(0)));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(
                snapshot(0, SessionType.WORK, IntervalState.PENDING, 0, 60, null, null, null)));
    }

    @Test
    void rejectsRemainingDurationAbovePlannedDuration() {
        SessionIntervalSnapshot invalid = new SessionIntervalSnapshot(IntervalId.generate(), 1,
                SessionType.WORK, seconds(60), seconds(0), seconds(61), IntervalState.PENDING,
                null, null, null);

        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(invalid));
    }

    @Test
    void rejectsMalformedPendingState() {
        SessionIntervalSnapshot withElapsedTime = snapshot(1, SessionType.WORK,
                IntervalState.PENDING, 1, 59, null, null, null);
        SessionIntervalSnapshot withStartTime = snapshot(1, SessionType.WORK,
                IntervalState.PENDING, 0, 60, T0, null, null);

        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(withElapsedTime));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(withStartTime));
    }

    @Test
    void rejectsMalformedRunningAndPausedStates() {
        SessionIntervalSnapshot withoutStart = snapshot(1, SessionType.WORK,
                IntervalState.RUNNING, 0, 60, null, null, null);
        SessionIntervalSnapshot alreadyEnded = snapshot(1, SessionType.WORK,
                IntervalState.RUNNING, 0, 60, T0, T1, null);
        SessionIntervalSnapshot pauseBeforeStart = snapshot(1, SessionType.WORK,
                IntervalState.RUNNING, 30, 30, T1, null, T0);

        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(withoutStart));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(alreadyEnded));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(pauseBeforeStart));
    }

    @Test
    void rejectsCompletedStateWithoutValidStartAndEnd() {
        SessionIntervalSnapshot withoutEnd = snapshot(1, SessionType.WORK,
                IntervalState.COMPLETED, 60, 0, T0, null, null);
        SessionIntervalSnapshot endBeforeStart = snapshot(1, SessionType.WORK,
                IntervalState.COMPLETED, 0, 60, T1, T0, null);

        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(withoutEnd));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(endBeforeStart));
    }

    @Test
    void rejectsSkippedWorkOrSkippedIntervalWithInconsistentValues() {
        SessionIntervalSnapshot skippedWork = snapshot(1, SessionType.WORK,
                IntervalState.SKIPPED, 0, 0, null, T0, null);
        SessionIntervalSnapshot skippedWithTime = snapshot(1, SessionType.SHORT_BREAK,
                IntervalState.SKIPPED, 1, 0, null, T0, null);

        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(skippedWork));
        assertThrows(IllegalArgumentException.class, () -> SessionIntervalFactory.reconstitute(skippedWithTime));
    }

    private static SessionIntervalSnapshot snapshot(int sequence, SessionType type, IntervalState state,
                                                    long actual, long remaining, TimeStamp started,
                                                    TimeStamp ended, TimeStamp paused) {
        return new SessionIntervalSnapshot(IntervalId.generate(), sequence, type, seconds(60),
                seconds(actual), seconds(remaining), state, started, ended, paused);
    }

    private static Duration seconds(long seconds) {
        return Duration.ofSeconds(seconds);
    }

    private static TimeStamp at(long seconds) {
        return new TimeStamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds));
    }
}
