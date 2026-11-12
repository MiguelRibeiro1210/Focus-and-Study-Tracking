package com.focusdesktop.application.usecase;

import static com.focusdesktop.application.usecase.SessionUseCaseTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.IntervalStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import org.junit.jupiter.api.Test;

class StartNextIntervalUseCaseTest {
    private static final SessionSettings MANUAL = new SessionSettings(Duration.ofSeconds(600),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false);

    @Test
    void startsPendingWorkIntervalAfterBreakAndPublishesStartedEvent() {
        FocusSession session = waitingAfterWork();
        var fixture = fixture(session, 601);

        var result = new StartNextIntervalUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.RUNNING, result.state());
        assertEquals(SessionType.SHORT_BREAK, result.intervalType());
        assertEquals(IntervalState.RUNNING, result.intervalState());
        assertEquals(1, fixture.events.size());
        assertEquals(SessionType.SHORT_BREAK, ((IntervalStarted) fixture.events.get(0)).intervalType());
    }

    @Test
    void rejectsEveryStateExceptWaitingWithAPendingInterval() {
        assertInvalidState(FocusSessionFactory.create(MANUAL, at(0))); // idle
        assertInvalidState(runningSession());

        FocusSession paused = runningSession();
        paused.pause(at(10));
        assertInvalidState(paused);

        FocusSession terminal = runningSession();
        terminal.stop(at(0));
        assertInvalidState(terminal);

        FocusSession waiting = waitingAfterWork();
        waiting.startNextInterval(at(601));
        assertInvalidState(waiting);
    }

    @Test
    void reportsMissingSessionWithoutWrites() {
        var fixture = fixture();
        var command = new SessionIdCommand(new com.focusdesktop.domain.model.SessionId(
                java.util.UUID.randomUUID()));

        assertThrows(SessionNotFoundException.class,
                () -> new StartNextIntervalUseCase(fixture, fixture, fixture.clock()).execute(command));
        assertEquals(0, fixture.saves);
        assertEquals(0, fixture.events.size());
    }

    private static void assertInvalidState(FocusSession session) {
        var fixture = fixture(session, 601);
        assertThrows(InvalidSessionStateException.class,
                () -> new StartNextIntervalUseCase(fixture, fixture, fixture.clock())
                        .execute(new SessionIdCommand(session.id())));
        assertEquals(0, fixture.saves);
        assertEquals(0, fixture.events.size());
    }

    private static FocusSession waitingAfterWork() {
        FocusSession session = FocusSessionFactory.create(MANUAL, at(0));
        session.start(at(0));
        session.completeInterval(SessionType.SHORT_BREAK, at(600));
        return session;
    }
}
