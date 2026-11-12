package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.InvalidIntervalTypeException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.BreakSkipped;
import com.focusdesktop.domain.event.IntervalStarted;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class SkipBreakUseCase {
    private final FocusSessionRepository sessions;
    private final DomainEventOutbox outbox;
    private final Clock clock;

    public SkipBreakUseCase(FocusSessionRepository sessions, DomainEventOutbox outbox, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public SessionDto execute(SessionIdCommand command) {
        Objects.requireNonNull(command, "command");
        FocusSession session = sessions.findById(command.sessionId())
                .orElseThrow(() -> new SessionNotFoundException(command.sessionId()));
        TimeStamp now = new TimeStamp(clock.instant());
        if (session.state() != SessionState.WAITING_FOR_NEXT_INTERVAL) {
            throw new InvalidSessionStateException("skip break", new IllegalStateException(
                    "Session is not waiting for a pending interval"));
        }
        SessionType skippedBreakType = session.currentInterval().type();
        if (skippedBreakType == SessionType.WORK) {
            throw new InvalidIntervalTypeException("skip break");
        }
        try {
            session.skipBreak(now);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new InvalidSessionStateException("skip break", exception);
        }
        sessions.save(session);
        outbox.append(new BreakSkipped(session.id(), skippedBreakType, now));
        var nextInterval = session.currentInterval();
        outbox.append(new IntervalStarted(session.id(), nextInterval.type(), session.state(),
                nextInterval.remainingDuration(now), now));
        return SessionDto.from(session, now);
    }
}
