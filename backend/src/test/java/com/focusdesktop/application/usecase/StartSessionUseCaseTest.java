package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.application.port.SessionSettingsProvider;
import com.focusdesktop.application.usecase.commands.StartSessionCommand;
import com.focusdesktop.application.usecase.exeptions.ActiveSessionExistsException;
import com.focusdesktop.domain.event.SessionStarted;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StartSessionUseCaseTest {
    private static final TimeStamp NOW = new TimeStamp(Instant.parse("2026-01-01T10:00:00Z"));
    private static final SessionSettings SETTINGS = new SessionSettings(Duration.ofSeconds(60),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 5, false, false);

    @Test
    void startsAndPersistsWorkSessionAndAppendsStartedEvent() {
        SessionUseCaseTestFixtures.Fixture fixture = SessionUseCaseTestFixtures.fixture();
        StartSessionUseCase useCase = useCase(fixture, () -> Optional.of(SETTINGS));

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
        assertEquals(result.sessionId(), fixture.lastSaved.id());
        assertEquals(SETTINGS, fixture.lastSaved.settings());
        assertEquals(1, fixture.events.size());
        SessionStarted event = (SessionStarted) fixture.events.get(0);
        assertEquals(result.sessionId(), event.sessionId());
        assertEquals(SessionType.WORK, event.sessionType());
        assertEquals(NOW, event.occurredAt());
    }

    @Test
    void usesSpecifiedDefaultsWhenProviderHasNoValidSettings() {
        SessionUseCaseTestFixtures.Fixture fixture = SessionUseCaseTestFixtures.fixture();
        StartSessionUseCase useCase = useCase(fixture, Optional::empty);

        useCase.execute(new StartSessionCommand(SessionType.WORK));

        assertEquals(new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(300),
                Duration.ofSeconds(600), 5, false, false), fixture.lastSaved.settings());
    }

    @Test
    void rejectsAnExistingActiveSessionBeforeLoadingSettingsOrWriting() {
        FocusSession active = FocusSessionFactory.create(SETTINGS, NOW);
        active.start(NOW);
        SessionUseCaseTestFixtures.Fixture fixture = SessionUseCaseTestFixtures.fixture(active, 0);
        SessionSettingsProvider settingsProvider = () -> {
            throw new AssertionError("Settings should not be loaded when a session is active");
        };
        StartSessionUseCase useCase = useCase(fixture, settingsProvider);

        assertThrows(ActiveSessionExistsException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.WORK)));
        assertEquals(0, fixture.saves);
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void rejectsPausedAndWaitingSessionsAsActive() {
        FocusSession paused = FocusSessionFactory.create(SETTINGS, NOW);
        paused.start(NOW);
        paused.pause(new TimeStamp(NOW.value().plusSeconds(10)));
        SessionUseCaseTestFixtures.Fixture pausedFixture = SessionUseCaseTestFixtures.fixture(paused, 0);
        FocusSession waiting = FocusSessionFactory.create(SETTINGS, NOW);
        waiting.start(NOW);
        waiting.completeInterval(SessionType.SHORT_BREAK,
                new TimeStamp(NOW.value().plusSeconds(60)));
        SessionUseCaseTestFixtures.Fixture waitingFixture = SessionUseCaseTestFixtures.fixture(waiting, 0);

        assertThrows(ActiveSessionExistsException.class,
                () -> useCase(pausedFixture, () -> Optional.of(SETTINGS))
                        .execute(new StartSessionCommand(SessionType.WORK)));
        assertThrows(ActiveSessionExistsException.class,
                () -> useCase(waitingFixture, () -> Optional.of(SETTINGS))
                        .execute(new StartSessionCommand(SessionType.WORK)));
    }

    @Test
    void rejectsNonWorkSessionTypeWithoutSideEffects() {
        SessionUseCaseTestFixtures.Fixture fixture = SessionUseCaseTestFixtures.fixture();
        StartSessionUseCase useCase = useCase(fixture, () -> Optional.of(SETTINGS));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.SHORT_BREAK)));
        assertEquals(0, fixture.activeLookups);
        assertEquals(0, fixture.saves);
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void propagatesPersistenceFailureWithoutAppendingStartedEvent() {
        SessionUseCaseTestFixtures.Fixture fixture = SessionUseCaseTestFixtures.fixture();
        fixture.failOnSave = true;
        StartSessionUseCase useCase = useCase(fixture, () -> Optional.of(SETTINGS));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.WORK)));
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void propagatesOutboxFailure() {
        SessionUseCaseTestFixtures.Fixture fixture = SessionUseCaseTestFixtures.fixture();
        fixture.failOnAppend = true;
        StartSessionUseCase useCase = useCase(fixture, () -> Optional.of(SETTINGS));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StartSessionCommand(SessionType.WORK)));
    }

    private static StartSessionUseCase useCase(SessionUseCaseTestFixtures.Fixture fixture,
                                               SessionSettingsProvider settingsProvider) {
        return new StartSessionUseCase(fixture, settingsProvider, fixture, fixture.clock());
    }
}
