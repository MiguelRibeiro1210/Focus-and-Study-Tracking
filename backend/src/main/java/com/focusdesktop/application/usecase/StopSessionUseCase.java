package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import com.focusdesktop.domain.event.SessionAbandoned;
import com.focusdesktop.domain.event.SessionCompleted;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class StopSessionUseCase {
    private final FocusSessionRepository sessions;
    private final DomainEventOutbox outbox;
    private final Clock clock;
    private final CompleteDueIntervalsUseCase completeDueIntervals;

    public StopSessionUseCase(FocusSessionRepository sessions, DomainEventOutbox outbox, Clock clock) {
        this(sessions, outbox, clock, null);
    }

    public StopSessionUseCase(FocusSessionRepository sessions, DomainEventOutbox outbox, Clock clock,
                             CompleteDueIntervalsUseCase completeDueIntervals) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.completeDueIntervals = completeDueIntervals;
    }

    @Transactional
    public SessionDto execute(SessionIdCommand command) {
        Objects.requireNonNull(command, "command");
        if (completeDueIntervals != null) {
            completeDueIntervals.execute();
        }
        FocusSession session = sessions.findById(command.sessionId())
                .orElseThrow(() -> new SessionNotFoundException(command.sessionId()));
        TimeStamp now = new TimeStamp(clock.instant());
        try {
            session.stop(now);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new InvalidSessionStateException("stop", exception);
        }

        sessions.save(session);
        if (session.state() == SessionState.COMPLETED) {
            outbox.append(new SessionCompleted(session.id(), now, session.actualFocusDuration()));
        } else {
            outbox.append(new SessionAbandoned(session.id(), now, session.actualFocusDuration(),
                    SessionAbandoned.BELOW_MINIMUM_FOCUS_TIME));
        }
        return SessionDto.from(session, now);
    }
}
