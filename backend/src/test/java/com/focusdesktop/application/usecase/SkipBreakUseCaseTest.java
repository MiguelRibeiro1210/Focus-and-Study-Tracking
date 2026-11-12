package com.focusdesktop.application.usecase;

import static com.focusdesktop.application.usecase.SessionUseCaseTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidIntervalTypeException;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.BreakSkipped;
import com.focusdesktop.domain.event.IntervalStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import org.junit.jupiter.api.Test;

class SkipBreakUseCaseTest {
    private static final SessionSettings MANUAL = new SessionSettings(Duration.ofSeconds(600),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false);

    @Test
    void skipsPendingShortBreakStartsWorkAndPublishesBothEvents() {
        FocusSession session = waitingAfterWork(SessionType.SHORT_BREAK);
        var fixture = fixture(session, 601);

        var result = new SkipBreakUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.RUNNING, result.state());
        assertEquals(SessionType.WORK, result.intervalType());
        assertEquals(IntervalState.RUNNING, result.intervalState());
        assertEquals(2, fixture.events.size());
        assertEquals(SessionType.SHORT_BREAK, ((BreakSkipped) fixture.events.get(0)).skippedBreakType());
        assertEquals(SessionType.WORK, ((IntervalStarted) fixture.events.get(1)).intervalType());
    }

    @Test
    void skipsPendingLongBreak() {
        FocusSession session = waitingAfterWork(SessionType.LONG_BREAK);
        var fixture = fixture(session, 601);

        var result = new SkipBreakUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionType.WORK, result.intervalType());
        assertEquals(SessionType.LONG_BREAK, ((BreakSkipped) fixture.events.get(0)).skippedBreakType());
    }

    @Test
    void rejectsNonWaitingStatesAndPendingWorkWithoutWrites() {
        assertInvalidState(FocusSessionFactory.create(MANUAL, at(0)));
        assertInvalidState(runningSession());

        FocusSession paused = runningSession();
        paused.pause(at(10));
        assertInvalidState(paused);

        FocusSession terminal = runningSession();
        terminal.stop(at(0));
        assertInvalidState(terminal);

        FocusSession pendingWork = waitingAfterWork(SessionType.SHORT_BREAK);
        pendingWork.startNextInterval(at(601));
        pendingWork.completeInterval(SessionType.WORK, at(1201));
        assertThrows(InvalidIntervalTypeException.class, () -> executeSkip(pendingWork));
    }

    @Test
    void reportsMissingSessionWithoutWrites() {
        var fixture = fixture();
        var command = new SessionIdCommand(new com.focusdesktop.domain.model.SessionId(
                java.util.UUID.randomUUID()));

        assertThrows(SessionNotFoundException.class,
                () -> new SkipBreakUseCase(fixture, fixture, fixture.clock()).execute(command));
        assertEquals(0, fixture.saves);
        assertEquals(0, fixture.events.size());
    }

    private static void assertInvalidState(FocusSession session) {
        var fixture = fixture(session, 601);
        assertThrows(InvalidSessionStateException.class, () -> executeSkip(session, fixture));
        assertEquals(0, fixture.saves);
        assertEquals(0, fixture.events.size());
    }

    private static void executeSkip(FocusSession session) {
        var fixture = fixture(session, 601);
        executeSkip(session, fixture);
    }

    private static void executeSkip(FocusSession session, SessionUseCaseTestFixtures.Fixture fixture) {
        new SkipBreakUseCase(fixture, fixture, fixture.clock()).execute(new SessionIdCommand(session.id()));
    }

    private static FocusSession waitingAfterWork(SessionType breakType) {
        FocusSession session = FocusSessionFactory.create(MANUAL, at(0));
        session.start(at(0));
        session.completeInterval(breakType, at(600));
        return session;
    }
}
