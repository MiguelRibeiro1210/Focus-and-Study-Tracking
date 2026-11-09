package com.focusdesktop.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionIntervalScheduleServiceTest {
    @Test
    void schedulesLongBreakAfterFourWorkIntervalsAndThenResetsCycle() {
        SessionSettings settings = new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(60),
                Duration.ofSeconds(120), 4, true, true);
        FocusSession session = FocusSessionFactory.create(settings, at(0));
        SessionIntervalScheduleService scheduler = new SessionIntervalScheduleService();
        session.start(at(0));
        long cycleStart = 0;

        for (int work = 1; work <= 4; work++) {
            session.completeInterval(scheduler.nextType(session), at(cycleStart + 60));
            SessionType expected = work == 4 ? SessionType.LONG_BREAK : SessionType.SHORT_BREAK;
            assertEquals(expected, session.intervals().get(session.intervals().size() - 1).type());
            long breakDuration = expected == SessionType.LONG_BREAK ? 120 : 60;
            cycleStart += 60 + breakDuration;
            session.completeInterval(scheduler.nextType(session), at(cycleStart));
            assertEquals(SessionType.WORK, session.intervals().get(session.intervals().size() - 1).type());
        }

        session.completeInterval(scheduler.nextType(session), at(cycleStart + 60));
        assertEquals(SessionType.SHORT_BREAK, session.intervals().get(session.intervals().size() - 1).type());
    }

    private static TimeStamp at(long seconds) {
        return new TimeStamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds));
    }
}
