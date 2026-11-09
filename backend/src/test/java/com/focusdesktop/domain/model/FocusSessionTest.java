package com.focusdesktop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.service.SessionIntervalScheduleService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FocusSessionTest {
    private static final TimeStamp T0 = at("2026-01-01T00:00:00Z");

    @Test
    void stoppingBelowFiveFocusMinutesAbandonsTheSession() {
        FocusSession session = runningSession();

        session.stop(at("2026-01-01T00:04:59Z"));

        assertEquals(SessionState.ABANDONED, session.state());
        assertEquals(299, session.actualFocusDuration().seconds());
    }

    @Test
    void stoppingAtFiveFocusMinutesCompletesTheSession() {
        FocusSession session = runningSession();

        session.stop(at("2026-01-01T00:05:00Z"));

        assertEquals(SessionState.COMPLETED, session.state());
        assertEquals(300, session.actualFocusDuration().seconds());
    }

    @Test
    void stoppingAboveFiveFocusMinutesCompletesTheSession() {
        FocusSession session = runningSession();

        session.stop(at("2026-01-01T00:05:01Z"));

        assertEquals(SessionState.COMPLETED, session.state());
        assertEquals(301, session.actualFocusDuration().seconds());
    }

    @Test
    void pausedTimeDoesNotCountTowardFiveMinuteThreshold() {
        FocusSession session = runningSession();
        session.pause(at("2026-01-01T00:02:00Z"));
        session.resume(at("2026-01-01T00:06:00Z"));

        session.stop(at("2026-01-01T00:09:00Z"));

        assertEquals(SessionState.COMPLETED, session.state());
        assertEquals(300, session.actualFocusDuration().seconds());
    }

    @Test
    void stoppingWhilePausedUsesOnlyWorkRecordedBeforeThePause() {
        FocusSession session = runningSession();
        session.pause(at("2026-01-01T00:02:00Z"));

        session.stop(at("2026-01-01T00:08:00Z"));

        assertEquals(SessionState.ABANDONED, session.state());
        assertEquals(120, session.actualFocusDuration().seconds());
        assertEquals(IntervalState.COMPLETED, session.intervals().get(0).state());
    }

    @Test
    void breakTimeDoesNotCountTowardFiveMinuteThreshold() {
        FocusSession session = runningSession();
        SessionIntervalScheduleService scheduler = new SessionIntervalScheduleService();
        session.completeInterval(scheduler.nextType(session), at("2026-01-01T00:05:00Z"));

        session.stop(at("2026-01-01T00:10:00Z"));

        assertEquals(SessionState.COMPLETED, session.state());
        assertEquals(300, session.actualFocusDuration().seconds());
    }

    @Test
    void terminalSessionCannotBeChanged() {
        FocusSession session = runningSession();
        session.stop(at("2026-01-01T00:05:00Z"));

        assertThrows(IllegalStateException.class, () -> session.start(at("2026-01-01T00:06:00Z")));
    }

    private static FocusSession runningSession() {
        FocusSession session = FocusSessionFactory.create(new SessionSettings(
                Duration.ofSeconds(60), Duration.ofSeconds(60), Duration.ofSeconds(120),
                4, true, true), T0);
        session.start(T0);
        return session;
    }

    private static TimeStamp at(String value) {
        return new TimeStamp(Instant.parse(value));
    }
}
