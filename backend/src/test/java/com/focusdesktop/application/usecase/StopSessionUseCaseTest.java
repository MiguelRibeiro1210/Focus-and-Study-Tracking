package com.focusdesktop.application.usecase;

import static com.focusdesktop.application.usecase.SessionUseCaseTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.SessionAbandoned;
import com.focusdesktop.domain.event.SessionCompleted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import org.junit.jupiter.api.Test;

class StopSessionUseCaseTest {
    @Test
    void abandonsRunningSessionBelowFiveMinuteThresholdWithStableReason() {
        var session = runningSession();
        var fixture = fixture(session, 299);

        var result = new StopSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.ABANDONED, result.state());
        assertEquals(299, result.actualFocusDuration().seconds());
        var event = (SessionAbandoned) fixture.events.get(0);
        assertEquals(299, event.actualFocusDuration().seconds());
        assertEquals(SessionAbandoned.BELOW_MINIMUM_FOCUS_TIME, event.reason());
    }

    @Test
    void completesRunningSessionAtExactlyFiveMinutes() {
        var session = runningSession();
        var fixture = fixture(session, 300);

        var result = new StopSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.COMPLETED, result.state());
        assertEquals(300, result.actualFocusDuration().seconds());
        var event = (SessionCompleted) fixture.events.get(0);
        assertEquals(300, event.actualFocusDuration().seconds());
    }

    @Test
    void stoppingPausedSessionDoesNotCountPausedWallClockTime() {
        var session = runningSession();
        session.pause(at(120));
        var fixture = fixture(session, 900);

        var result = new StopSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.ABANDONED, result.state());
        assertEquals(120, result.actualFocusDuration().seconds());
        assertEquals(IntervalState.COMPLETED, result.intervalState());
        assertEquals(120, ((SessionAbandoned) fixture.events.get(0)).actualFocusDuration().seconds());
    }

    @Test
    void stopsWhileWaitingAndKeepsPendingBreakOutOfFocusDuration() {
        SessionSettings oneMinuteWork = new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(300),
                Duration.ofSeconds(600), 5, false, false);
        FocusSession session = FocusSessionFactory.create(oneMinuteWork, at(0));
        session.start(at(0));
        session.completeInterval(com.focusdesktop.domain.model.SessionType.SHORT_BREAK, at(60));
        var fixture = fixture(session, 61);

        var result = new StopSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.ABANDONED, result.state());
        assertEquals(60, result.actualFocusDuration().seconds());
        assertEquals(IntervalState.PENDING, result.intervalState());
        assertEquals(60, ((SessionAbandoned) fixture.events.get(0)).actualFocusDuration().seconds());
    }

    @Test
    void rejectsMissingAndIdleSessionsWithoutWrites() {
        var session = FocusSessionFactory.create(TEN_MINUTES, at(0));
        var fixture = fixture(session, 10);
        var useCase = new StopSessionUseCase(fixture, fixture, fixture.clock());
        assertThrows(SessionNotFoundException.class,
                () -> useCase.execute(new SessionIdCommand(new com.focusdesktop.domain.model.SessionId(
                        java.util.UUID.randomUUID()))));
        assertThrows(InvalidSessionStateException.class,
                () -> useCase.execute(new SessionIdCommand(session.id())));
        assertTrue(fixture.events.isEmpty());
        assertTrue(fixture.writes.isEmpty());
    }

    @Test
    void savesBeforeAppendingAndPropagatesOutboxFailure() {
        var session = runningSession();
        var fixture = fixture(session, 300);
        fixture.failOnAppend = true;

        assertThrows(IllegalStateException.class, () -> new StopSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id())));

        assertEquals(java.util.List.of("save", "append"), fixture.writes);
    }

    @Test
    void propagatesPersistenceFailureWithoutAppendingAnEvent() {
        var session = runningSession();
        var fixture = fixture(session, 300);
        fixture.failOnSave = true;

        assertThrows(IllegalStateException.class, () -> new StopSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id())));

        assertTrue(fixture.events.isEmpty());
        assertEquals(java.util.List.of("save"), fixture.writes);
    }
}
