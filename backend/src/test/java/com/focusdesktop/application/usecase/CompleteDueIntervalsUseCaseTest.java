package com.focusdesktop.application.usecase;

import static com.focusdesktop.application.usecase.SessionUseCaseTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.focusdesktop.domain.event.IntervalCompleted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.service.SessionIntervalScheduleService;
import org.junit.jupiter.api.Test;

class CompleteDueIntervalsUseCaseTest {
    @Test
    void completesDueWorkToShortBreakAndShortBreakToWork() {
        SessionSettings settings = settings(2, true, true);
        FocusSession session = running(settings);
        var fixture = fixture(session, 60);
        assertEquals(1, complete(fixture));
        assertEquals(SessionType.SHORT_BREAK, session.currentInterval().type());
        assertEquals(SessionState.RUNNING, session.state());
        fixture.clockSeconds = 120;

        assertEquals(1, complete(fixture));
        assertEquals(SessionType.WORK, session.currentInterval().type());
        assertEquals(2, fixture.events.stream().filter(IntervalCompleted.class::isInstance).count());
    }

    @Test
    void selectsLongBreakAtConfiguredWorkIntervalCount() {
        SessionSettings settings = settings(2, true, true);
        FocusSession session = running(settings);
        var fixture = fixture(session, 60);
        complete(fixture); // work 1 -> short break
        fixture.clockSeconds = 120;
        complete(fixture); // short break -> work 2
        fixture.clockSeconds = 180;
        complete(fixture); // work 2 -> long break

        assertEquals(SessionType.LONG_BREAK, session.currentInterval().type());
        assertEquals(SessionType.LONG_BREAK,
                ((IntervalCompleted) fixture.events.get(fixture.events.size() - 2)).nextIntervalType());
    }

    @Test
    void leavesPausedAndWaitingSessionsUnchanged() {
        FocusSession paused = running(settings(2, true, true));
        paused.pause(at(10));
        var pausedFixture = fixture(paused, 600);
        assertEquals(0, complete(pausedFixture));
        assertEquals(SessionState.PAUSED, paused.state());
        assertEquals(0, pausedFixture.events.size());

        FocusSession waiting = running(settings(2, false, true));
        waiting.completeInterval(SessionType.SHORT_BREAK, at(60));
        var waitingFixture = fixture(waiting, 600);
        assertEquals(0, complete(waitingFixture));
        assertEquals(SessionState.WAITING_FOR_NEXT_INTERVAL, waiting.state());
        assertEquals(0, waitingFixture.events.size());
    }

    @Test
    void repeatedRunDoesNotCompleteAnIntervalTwiceOrDuplicateEvents() {
        FocusSession session = running(settings(2, false, false));
        var fixture = fixture(session, 60);
        assertEquals(1, complete(fixture));
        int eventCountAfterCompletion = fixture.events.size();
        assertEquals(SessionState.WAITING_FOR_NEXT_INTERVAL, session.state());
        assertEquals(0, complete(fixture));
        assertEquals(eventCountAfterCompletion, fixture.events.size());
        assertEquals(IntervalState.PENDING, session.currentInterval().state());
    }

    private static FocusSession running(SessionSettings settings) {
        FocusSession session = FocusSessionFactory.create(settings, at(0));
        session.start(at(0));
        return session;
    }

    private static int complete(SessionUseCaseTestFixtures.Fixture fixture) {
        return new CompleteDueIntervalsUseCase(fixture, fixture,
                new SessionIntervalScheduleService(), fixture.clock()).execute();
    }

    private static SessionSettings settings(int intervalsBeforeLongBreak,
            boolean autoStartBreaks, boolean autoStartWork) {
        return new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(60), Duration.ofSeconds(120),
                intervalsBeforeLongBreak, autoStartBreaks, autoStartWork);
    }
}
