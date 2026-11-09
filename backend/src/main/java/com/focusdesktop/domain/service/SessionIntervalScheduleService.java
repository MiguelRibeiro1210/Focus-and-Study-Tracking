package com.focusdesktop.domain.service;

import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionInterval;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;

public final class SessionIntervalScheduleService {
    public SessionType nextType(FocusSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        if (session.state() != SessionState.RUNNING || session.intervals().isEmpty()) {
            throw new IllegalStateException("Next interval can only be selected for a running session");
        }

        SessionInterval current = session.currentInterval();
        if (current.state() != IntervalState.RUNNING || current.isPaused()) {
            throw new IllegalStateException("Current interval is not actively running");
        }
        if (current.type() != SessionType.WORK) {
            return SessionType.WORK;
        }

        int completedWorkIntervals = (int) session.intervals().stream()
                .filter(interval -> interval.type() == SessionType.WORK
                        && interval.state() == IntervalState.COMPLETED)
                .count() + 1;
        SessionSettings settings = session.settings();
        return completedWorkIntervals % settings.intervalsBeforeLongBreak() == 0
                ? SessionType.LONG_BREAK
                : SessionType.SHORT_BREAK;
    }
}
