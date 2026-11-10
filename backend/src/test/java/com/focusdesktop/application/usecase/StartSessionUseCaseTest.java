package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.application.port.SessionSettingsProvider;
import com.focusdesktop.domain.event.DomainEvent;
import com.focusdesktop.domain.event.SessionStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StartSessionUseCaseTest {
    private static final TimeStamp NOW = new TimeStamp(Instant.parse("2026-01-01T10:00:00Z"));
    private static final SessionSettings SETTINGS = new SessionSettings(Duration.ofSeconds(60),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 5, false, false);

    @Test
    void startsAndPersistsWorkSessionAndAppendsStartedEvent() {
        InMemorySessions sessions = new InMemorySessions();
        RecordingOutbox outbox = new RecordingOutbox();
        StartSessionUseCase useCase = useCase(sessions, () -> Optional.of(SETTINGS), outbox);

        SessionDto result = useCase.execute(new StartSessionCommand(SessionType.WORK));

        assertEquals(SessionState.RUNNING, result.state());
        assertEquals(SessionType.WORK, result.intervalType());
        assertEquals(IntervalState.RUNNING, result.intervalState());
        assertEquals(1, result.intervalSequence());
        assertEquals(60, result.plannedDuration().seconds());
        assertEquals(0, result.actualFocusDuration().seconds());
        assertEquals(60, result.remainingDuration().seconds());
        assertEquals(NOW, result.createdAt());
        assertEquals(NOW, result.startedAt());
        assertEquals(result.sessionId(), sessions.saved.id());
        assertEquals(SETTINGS, sessions.saved.settings());
        assertEquals(1, outbox.events.size());
        SessionStarted event = (SessionStarted) outbox.events.get(0);
        assertEquals(result.sessionId(), event.sessionId());
        assertEquals(SessionType.WORK, event.sessionType());
        assertEquals(NOW, event.occurredAt());
    }

    @Test
    void usesSpecifiedDefaultsWhenProviderHasNoValidSettings() {
        InMemorySessions sessions = new InMemorySessions();
        StartSessionUseCase useCase = useCase(sessions, () -> Optional.empty(), new RecordingOutbox());

        useCase.execute(new StartSessionCommand(SessionType.WORK));

        assertEquals(new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(300),
                Duration.ofSeconds(600), 5, false, false), sessions.saved.settings());
    }

    @Test
    void rejectsAnExistingActiveSessionBeforeLoadingSettingsOrWriting() {
        InMemorySessions sessions = new InMemorySessions();
        FocusSession active = FocusSessionFactory.create(SETTINGS, NOW);
        active.start(NOW);
        sessions.active = active;
        RecordingOutbox outbox = new RecordingOutbox();
        SessionSettingsProvider settingsProvider = () -> {
            throw new AssertionError("Settings should not be loaded when a session is active");
        };
        StartSessionUseCase useCase = useCase(sessions, settingsProvider, outbox);

        assertThrows(ActiveSessionExistsException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.WORK)));
        assertEquals(0, sessions.saves);
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void rejectsPausedAndWaitingSessionsAsActive() {
        FocusSession paused = FocusSessionFactory.create(SETTINGS, NOW);
        paused.start(NOW);
        paused.pause(new TimeStamp(NOW.value().plusSeconds(10)));
        InMemorySessions pausedSessions = new InMemorySessions();
        pausedSessions.active = paused;
        FocusSession waiting = FocusSessionFactory.create(SETTINGS, NOW);
        waiting.start(NOW);
        waiting.completeInterval(SessionType.SHORT_BREAK,
                new TimeStamp(NOW.value().plusSeconds(60)));
        InMemorySessions waitingSessions = new InMemorySessions();
        waitingSessions.active = waiting;

        assertThrows(ActiveSessionExistsException.class,
                () -> useCase(pausedSessions, () -> Optional.of(SETTINGS), new RecordingOutbox())
                        .execute(new StartSessionCommand(SessionType.WORK)));
        assertThrows(ActiveSessionExistsException.class,
                () -> useCase(waitingSessions, () -> Optional.of(SETTINGS), new RecordingOutbox())
                        .execute(new StartSessionCommand(SessionType.WORK)));
    }

    @Test
    void rejectsNonWorkSessionTypeWithoutSideEffects() {
        InMemorySessions sessions = new InMemorySessions();
        RecordingOutbox outbox = new RecordingOutbox();
        StartSessionUseCase useCase = useCase(sessions, () -> Optional.of(SETTINGS), outbox);

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.SHORT_BREAK)));
        assertEquals(0, sessions.lookups);
        assertEquals(0, sessions.saves);
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void propagatesPersistenceFailureWithoutAppendingStartedEvent() {
        InMemorySessions sessions = new InMemorySessions();
        sessions.failOnSave = true;
        RecordingOutbox outbox = new RecordingOutbox();
        StartSessionUseCase useCase = useCase(sessions, () -> Optional.of(SETTINGS), outbox);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.WORK)));
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void propagatesOutboxFailure() {
        InMemorySessions sessions = new InMemorySessions();
        RecordingOutbox outbox = new RecordingOutbox();
        outbox.failOnAppend = true;
        StartSessionUseCase useCase = useCase(sessions, () -> Optional.of(SETTINGS), outbox);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.WORK)));
    }

    private static StartSessionUseCase useCase(InMemorySessions sessions,
                                               SessionSettingsProvider settingsProvider,
                                               RecordingOutbox outbox) {
        Clock clock = Clock.fixed(NOW.value(), ZoneOffset.UTC);
        return new StartSessionUseCase(sessions, settingsProvider, outbox, clock);
    }

    private static final class InMemorySessions implements FocusSessionRepository {
        private FocusSession active;
        private FocusSession saved;
        private int lookups;
        private int saves;
        private boolean failOnSave;

        @Override
        public Optional<FocusSession> findActive() {
            lookups++;
            return Optional.ofNullable(active);
        }

        @Override
        public Optional<FocusSession> findById(SessionId sessionId) {
            if (active != null && active.id().equals(sessionId)) {
                return Optional.of(active);
            }
            if (saved != null && saved.id().equals(sessionId)) {
                return Optional.of(saved);
            }
            return Optional.empty();
        }

        @Override
        public void save(FocusSession session) {
            saves++;
            if (failOnSave) {
                throw new IllegalStateException("Persistence failed");
            }
            saved = session;
        }
    }

    private static final class RecordingOutbox implements DomainEventOutbox {
        private final List<DomainEvent> events = new ArrayList<>();
        private boolean failOnAppend;

        @Override
        public void append(DomainEvent event) {
            if (failOnAppend) {
                throw new IllegalStateException("Outbox append failed");
            }
            events.add(event);
        }
    }
}
