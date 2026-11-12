package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public class GetActiveSessionUseCase {
    private final FocusSessionRepository sessions;
    private final Clock clock;

    public GetActiveSessionUseCase(FocusSessionRepository sessions, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<SessionDto> execute() {
        return sessions.findActive().map(session -> SessionDto.from(session,
                new com.focusdesktop.domain.model.TimeStamp(clock.instant())));
    }
}
