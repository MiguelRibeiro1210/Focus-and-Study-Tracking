package com.focusdesktop.domain.factories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import com.focusdesktop.domain.factories.snapshots.FocusSessionSnapshot;
import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;
import com.focusdesktop.domain.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FocusSessionFactoryTest {
    private static final TimeStamp T0 = at("2026-01-01T00:00:00Z");

    @Test
    void createsIdleSessionAndReconstitutesItsSnapshot() {
        FocusSession session = FocusSessionFactory.create(settings(), T0);

        Assertions.assertEquals(SessionState.IDLE, session.state());
        assertEquals(session.id(), FocusSessionFactory.reconstitute(session.snapshot()).id());
        assertEquals(SessionState.IDLE, FocusSessionFactory.reconstitute(session.snapshot()).state());
    }

    @Test
    void reconstitutesPausedSessionAndPreservesPausedIntervalState() {
        FocusSession session = FocusSessionFactory.create(settings(), T0);
        session.start(T0);
        session.pause(at("2026-01-01T00:01:00Z"));

        FocusSession restored = FocusSessionFactory.reconstitute(session.snapshot());

        assertEquals(SessionState.PAUSED, restored.state());
        assertTrue(restored.intervals().get(0).isPaused());
        assertEquals(60, restored.actualFocusDuration().seconds());
    }

    @Test
    void reconstitutesRunningSession() {
        FocusSession session = startedSession(settings());

        FocusSession restored = FocusSessionFactory.reconstitute(session.snapshot());

        assertEquals(SessionState.RUNNING, restored.state());
        Assertions.assertEquals(IntervalState.RUNNING, restored.intervals().get(0).state());
        assertFalse(restored.intervals().get(0).isPaused());
    }

    @Test
    void reconstitutesWaitingSessionWithItsPendingBreak() {
        FocusSession session = startedSession(settings(false));
        session.completeInterval(SessionType.SHORT_BREAK, at("2026-01-01T00:01:00Z"));

        FocusSession restored = FocusSessionFactory.reconstitute(session.snapshot());

        assertEquals(SessionState.WAITING_FOR_NEXT_INTERVAL, restored.state());
        assertEquals(2, restored.intervals().size());
        assertEquals(SessionType.SHORT_BREAK, restored.intervals().get(1).type());
        assertEquals(IntervalState.PENDING, restored.intervals().get(1).state());
    }

    @Test
    void reconstitutesBothTerminalStates() {
        FocusSession completed = startedSession(settings());
        completed.stop(at("2026-01-01T00:05:00Z"));
        FocusSession abandoned = startedSession(settings());
        abandoned.stop(at("2026-01-01T00:04:59Z"));

        assertEquals(SessionState.COMPLETED,
                FocusSessionFactory.reconstitute(completed.snapshot()).state());
        assertEquals(SessionState.ABANDONED,
                FocusSessionFactory.reconstitute(abandoned.snapshot()).state());
    }

    @Test
    void rejectsSnapshotWhoseIntervalSequenceIsNotContiguous() {
        SessionIntervalSnapshot interval = new SessionIntervalSnapshot(
                IntervalId.generate(), 2, SessionType.WORK, Duration.ofSeconds(60),
                Duration.ofSeconds(0), Duration.ofSeconds(60), IntervalState.PENDING,
                null, null, null);
        FocusSessionSnapshot snapshot = new FocusSessionSnapshot(SessionId.generate(), SessionType.WORK,
                settings(), T0, SessionState.IDLE, null, null, List.of(interval));

        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(snapshot));
    }

    @Test
    void rejectsRepeatedIntervalIds() {
        SessionIntervalSnapshot first = completedWorkInterval(1, IntervalId.generate());
        SessionIntervalSnapshot pendingBreak = pendingBreakInterval(2, first.id());
        FocusSessionSnapshot snapshot = snapshot(SessionState.WAITING_FOR_NEXT_INTERVAL,
                at("2026-01-01T00:00:00Z"), null, List.of(first, pendingBreak));

        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(snapshot));
    }

    @Test
    void rejectsIntervalsThatDoNotAlternateStartingWithWork() {
        SessionIntervalSnapshot first = pendingBreakInterval(1, IntervalId.generate());
        FocusSessionSnapshot snapshot = snapshot(SessionState.IDLE, null, null, List.of(first));

        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(snapshot));
    }

    @Test
    void rejectsNonFinalPendingOrActiveIntervals() {
        SessionIntervalSnapshot firstPending = pendingWorkInterval(1, IntervalId.generate());
        SessionIntervalSnapshot secondPending = pendingBreakInterval(2, IntervalId.generate());
        FocusSessionSnapshot waitingWithEarlierPending = snapshot(SessionState.WAITING_FOR_NEXT_INTERVAL,
                at("2026-01-01T00:00:00Z"), null, List.of(firstPending, secondPending));
        SessionIntervalSnapshot firstActive = runningWorkInterval(1, IntervalId.generate());
        SessionIntervalSnapshot pendingBreak = pendingBreakInterval(2, IntervalId.generate());
        FocusSessionSnapshot waitingWithEarlierActive = snapshot(SessionState.WAITING_FOR_NEXT_INTERVAL,
                at("2026-01-01T00:00:00Z"), null, List.of(firstActive, pendingBreak));

        assertThrows(IllegalArgumentException.class,
                () -> FocusSessionFactory.reconstitute(waitingWithEarlierPending));
        assertThrows(IllegalArgumentException.class,
                () -> FocusSessionFactory.reconstitute(waitingWithEarlierActive));
    }

    @Test
    void rejectsCurrentIntervalInconsistentWithSessionState() {
        SessionIntervalSnapshot pending = pendingWorkInterval(1, IntervalId.generate());
        FocusSessionSnapshot runningWithPendingInterval = snapshot(SessionState.RUNNING,
                at("2026-01-01T00:00:00Z"), null, List.of(pending));

        assertThrows(IllegalArgumentException.class,
                () -> FocusSessionFactory.reconstitute(runningWithPendingInterval));
    }

    @Test
    void rejectsTerminalSessionWithAnActiveIntervalOrMissingEndTime() {
        SessionIntervalSnapshot active = runningWorkInterval(1, IntervalId.generate());
        FocusSessionSnapshot terminalWithActiveInterval = snapshot(SessionState.COMPLETED,
                at("2026-01-01T00:00:00Z"), at("2026-01-01T00:01:00Z"), List.of(active));
        FocusSessionSnapshot terminalWithoutEnd = snapshot(SessionState.COMPLETED,
                at("2026-01-01T00:00:00Z"), null, List.of(completedWorkInterval(1, IntervalId.generate())));

        assertThrows(IllegalArgumentException.class,
                () -> FocusSessionFactory.reconstitute(terminalWithActiveInterval));
        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(terminalWithoutEnd));
    }

    @Test
    void rejectsIdleSessionWithLifecycleDataAndStartedSessionWithoutStartTime() {
        FocusSessionSnapshot idleWithStart = snapshot(SessionState.IDLE,
                at("2026-01-01T00:00:00Z"), null, List.of());
        FocusSessionSnapshot runningWithoutStart = snapshot(SessionState.RUNNING,
                null, null, List.of(runningWorkInterval(1, IntervalId.generate())));

        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(idleWithStart));
        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(runningWithoutStart));
    }

    @Test
    void rejectsNonWorkSessionType() {
        FocusSessionSnapshot snapshot = new FocusSessionSnapshot(SessionId.generate(), SessionType.SHORT_BREAK,
                settings(), T0, SessionState.IDLE, null, null, List.of());

        assertThrows(IllegalArgumentException.class, () -> FocusSessionFactory.reconstitute(snapshot));
    }

    private static FocusSession startedSession(SessionSettings settings) {
        FocusSession session = FocusSessionFactory.create(settings, T0);
        session.start(T0);
        return session;
    }

    private static FocusSessionSnapshot snapshot(SessionState state, TimeStamp startedAt,
                                                 TimeStamp endedAt, List<SessionIntervalSnapshot> intervals) {
        return new FocusSessionSnapshot(SessionId.generate(), SessionType.WORK, settings(),
                T0, state, startedAt, endedAt, intervals);
    }

    private static SessionIntervalSnapshot pendingWorkInterval(int sequence, IntervalId id) {
        return new SessionIntervalSnapshot(id, sequence, SessionType.WORK, Duration.ofSeconds(60),
                Duration.ofSeconds(0), Duration.ofSeconds(60), IntervalState.PENDING, null, null, null);
    }

    private static SessionIntervalSnapshot pendingBreakInterval(int sequence, IntervalId id) {
        return new SessionIntervalSnapshot(id, sequence, SessionType.SHORT_BREAK, Duration.ofSeconds(60),
                Duration.ofSeconds(0), Duration.ofSeconds(60), IntervalState.PENDING, null, null, null);
    }

    private static SessionIntervalSnapshot runningWorkInterval(int sequence, IntervalId id) {
        return new SessionIntervalSnapshot(id, sequence, SessionType.WORK, Duration.ofSeconds(60),
                Duration.ofSeconds(0), Duration.ofSeconds(60), IntervalState.RUNNING, T0, null, null);
    }

    private static SessionIntervalSnapshot completedWorkInterval(int sequence, IntervalId id) {
        return new SessionIntervalSnapshot(id, sequence, SessionType.WORK, Duration.ofSeconds(60),
                Duration.ofSeconds(60), Duration.ofSeconds(0), IntervalState.COMPLETED,
                T0, at("2026-01-01T00:01:00Z"), null);
    }

    private static SessionSettings settings() {
        return settings(true);
    }

    private static SessionSettings settings(boolean autoStartBreaks) {
        return new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(60),
                Duration.ofSeconds(120), 4, autoStartBreaks, true);
    }

    private static TimeStamp at(String value) {
        return new TimeStamp(Instant.parse(value));
    }
}
