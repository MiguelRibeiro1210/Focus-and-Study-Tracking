package com.focusdesktop.application.usecase;

import static com.focusdesktop.application.usecase.SessionUseCaseTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.SessionPausedOrResumed;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionState;
import org.junit.jupiter.api.Test;

class PauseSessionUseCaseTest {
    @Test
    void pausesRunningSessionPersistsAndAppendsEvent() {
        var session = runningSession();
        var fixture = fixture(session, 120);

        var result = new PauseSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.PAUSED, result.state());
        assertEquals(IntervalState.RUNNING, result.intervalState());
        assertEquals(480, result.remainingDuration().seconds());
        assertEquals(120, result.actualFocusDuration().seconds());
        assertEquals(1, fixture.events.size());
        var event = (SessionPausedOrResumed) fixture.events.get(0);
        assertEquals(session.id(), event.sessionId());
        assertEquals(at(120), event.occurredAt());
        assertEquals(480, event.remainingDuration().seconds());
        assertEquals(java.util.List.of("save", "append"), fixture.writes);
    }

    @Test
    void rejectsMissingAndNonRunningSessionsWithoutWrites() {
        var session = runningSession();
        var fixture = fixture(session, 120);
        var useCase = new PauseSessionUseCase(fixture, fixture, fixture.clock());
        assertThrows(SessionNotFoundException.class,
                () -> useCase.execute(new SessionIdCommand(new com.focusdesktop.domain.model.SessionId(
                        java.util.UUID.randomUUID()))));

        session.pause(at(60));
        fixture.writes.clear();
        assertThrows(InvalidSessionStateException.class,
                () -> useCase.execute(new SessionIdCommand(session.id())));
        assertTrue(fixture.events.isEmpty());
        assertTrue(fixture.writes.isEmpty());
    }
}
