package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.SessionPausedOrResumed;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class PauseSessionUseCase {
    private final FocusSessionRepository sessions;
    private final DomainEventOutbox outbox;
    private final Clock clock;

    public PauseSessionUseCase(FocusSessionRepository sessions, DomainEventOutbox outbox, Clock clock) {
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
        try {
            session.pause(now);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new InvalidSessionStateException("pause", exception);
        }
        sessions.save(session);
        outbox.append(new SessionPausedOrResumed(session.id(), SessionState.PAUSED, now,
                session.currentInterval().remainingDuration()));
        return SessionDto.from(session, now);
    }
}
