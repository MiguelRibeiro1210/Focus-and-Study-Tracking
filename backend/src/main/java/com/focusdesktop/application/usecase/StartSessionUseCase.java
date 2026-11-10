package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.application.port.SessionSettingsProvider;
import com.focusdesktop.domain.event.SessionStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.logging.Logger;
import org.springframework.transaction.annotation.Transactional;

public class StartSessionUseCase {
    private static final Logger LOGGER = Logger.getLogger(StartSessionUseCase.class.getName());
    private static final SessionSettings DEFAULT_SETTINGS = new SessionSettings(
            Duration.ofSeconds(60), Duration.ofSeconds(5 * 60), Duration.ofSeconds(10 * 60),
            5, false, false);

    private final FocusSessionRepository sessions;
    private final SessionSettingsProvider settingsProvider;
    private final DomainEventOutbox outbox;
    private final Clock clock;

    public StartSessionUseCase(FocusSessionRepository sessions,
                               SessionSettingsProvider settingsProvider,
                               DomainEventOutbox outbox,
                               Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.settingsProvider = Objects.requireNonNull(settingsProvider, "settingsProvider");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public SessionDto execute(StartSessionCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.sessionType() != SessionType.WORK) {
            throw new IllegalArgumentException("A focus session must start with WORK");
        }
        if (sessions.findActive().isPresent()) {
            throw new ActiveSessionExistsException();
        }

        SessionSettings settings = settingsProvider.findCurrentSettings()
                .orElseGet(StartSessionUseCase::defaultSettingsWithWarning);
        TimeStamp now = new TimeStamp(clock.instant());
        FocusSession session = FocusSessionFactory.create(settings, now);
        session.start(now);

        sessions.save(session);
        outbox.append(new SessionStarted(session.id(), session.sessionType(), now));
        return SessionDto.from(session, now);
    }

    private static SessionSettings defaultSettingsWithWarning() {
        LOGGER.warning("Timer settings are missing or invalid; using default timer settings");
        return DEFAULT_SETTINGS;
    }
}
