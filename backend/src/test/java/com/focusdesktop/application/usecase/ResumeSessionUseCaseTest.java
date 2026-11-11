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

class ResumeSessionUseCaseTest {
    @Test
    void resumesPausedIntervalAndPreservesItsElapsedAndRemainingTime() {
        var session = runningSession();
        var intervalId = session.currentInterval().id();
        session.pause(at(120));
        var fixture = fixture(session, 300);

        var result = new ResumeSessionUseCase(fixture, fixture, fixture.clock())
                .execute(new SessionIdCommand(session.id()));

        assertEquals(SessionState.RUNNING, result.state());
        assertEquals(IntervalState.RUNNING, result.intervalState());
        assertEquals(intervalId, session.currentInterval().id());
        assertEquals(1, result.intervalSequence());
        assertEquals(120, result.actualFocusDuration().seconds());
        assertEquals(480, result.remainingDuration().seconds());
        assertEquals(1, fixture.events.size());
        var event = (SessionPausedOrResumed) fixture.events.get(0);
        assertEquals(SessionState.RUNNING, event.resultingState());
        assertEquals(at(300), event.occurredAt());
        assertEquals(java.util.List.of("save", "append"), fixture.writes);
    }

    @Test
    void rejectsMissingAndNonPausedSessionsWithoutWrites() {
        var session = runningSession();
        var fixture = fixture(session, 120);
        var useCase = new ResumeSessionUseCase(fixture, fixture, fixture.clock());
        assertThrows(SessionNotFoundException.class,
                () -> useCase.execute(new SessionIdCommand(new com.focusdesktop.domain.model.SessionId(
                        java.util.UUID.randomUUID()))));
        assertThrows(InvalidSessionStateException.class,
                () -> useCase.execute(new SessionIdCommand(session.id())));
        assertTrue(fixture.events.isEmpty());
        assertTrue(fixture.writes.isEmpty());
    }
}
