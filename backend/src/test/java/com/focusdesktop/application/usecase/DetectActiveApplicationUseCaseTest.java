package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.focusdesktop.application.port.ActiveApplicationPort;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DetectActiveApplicationUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-26T00:00:10Z");
    private final FocusSessionRepository sessions = mock(FocusSessionRepository.class);
    private final ActiveApplicationPort apps = mock(ActiveApplicationPort.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void disabledOrAbsentSessionNeverQueriesOperatingSystem() {
        var disabled = new DetectActiveApplicationUseCase(sessions, apps, clock, false);
        assertTrue(disabled.execute().isEmpty());
        assertEquals("DISABLED", disabled.status().applicationDetection());
        verifyNoInteractions(sessions, apps);
        when(sessions.findActive()).thenReturn(Optional.empty());
        assertTrue(monitor().execute().isEmpty());
        verifyNoInteractions(apps);
    }

    @Test
    void pausedWaitingBreakStoppedAndExpiredWorkNeverCollect() {
        var paused = running(); paused.pause(at(5));
        var waiting = running(); waiting.completeInterval(SessionType.SHORT_BREAK, at(60));
        var onBreak = running(); onBreak.completeInterval(SessionType.SHORT_BREAK, at(60)); onBreak.startNextInterval(at(60));
        var stopped = running(); stopped.stop(at(5));
        for (var session : new FocusSession[] { paused, waiting, onBreak, stopped }) {
            when(sessions.findActive()).thenReturn(Optional.of(session));
            assertTrue(monitor().execute().isEmpty());
        }
        when(sessions.findActive()).thenReturn(Optional.of(running()));
        var expired = new DetectActiveApplicationUseCase(sessions, apps, Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC), true);
        assertTrue(expired.execute().isEmpty());
        verifyNoInteractions(apps);
    }

    @Test
    void observesOnlyEligibleWorkAndRetainsTransientInstanceIdentity() {
        var session = running(); when(sessions.findActive()).thenReturn(Optional.of(session));
        var observation = observation(); when(apps.currentApplication()).thenReturn(Optional.of(observation));
        var monitor = monitor(); var result = monitor.execute().orElseThrow();
        assertEquals(session.id(), result.sessionId());
        assertEquals(session.currentInterval().id(), result.intervalId());
        assertEquals(observation, result.observation());
        assertEquals("OBSERVING", monitor.status().applicationDetection());
        assertFalse(monitor.status().applicationEnforcement());
        verify(sessions, times(2)).findActive();
    }

    @Test
    void discardsObservationIfSessionPausesOrIsReplacedDuringOsLookup() {
        var session = running(); when(sessions.findActive()).thenReturn(Optional.of(session));
        when(apps.currentApplication()).thenAnswer(call -> { session.pause(at(10)); return Optional.of(observation()); });
        var monitor = monitor(); assertTrue(monitor.execute().isEmpty());
        assertEquals("IDLE", monitor.status().applicationDetection());
        doReturn(Optional.of(observation())).when(apps).currentApplication();
        when(sessions.findActive()).thenReturn(Optional.of(running()), Optional.of(running()));
        assertTrue(monitor.execute().isEmpty());
    }

    @Test
    void reportsCapabilityFailureWithoutLeakingRawToolErrorsAndRecovers() {
        when(sessions.findActive()).thenReturn(Optional.of(running()));
        when(apps.currentApplication()).thenThrow(new IllegalStateException("Private window title from tool"))
                .thenReturn(Optional.empty());
        var monitor = monitor(); assertTrue(monitor.execute().isEmpty());
        assertEquals("UNAVAILABLE", monitor.status().applicationDetection());
        assertFalse(monitor.status().applicationReason().contains("Private"));
        assertTrue(monitor.execute().isEmpty());
        assertEquals("OBSERVING", monitor.status().applicationDetection());
    }

    private DetectActiveApplicationUseCase monitor() { return new DetectActiveApplicationUseCase(sessions, apps, clock, true); }
    private static ActiveApplicationPort.Observation observation() {
        return new ActiveApplicationPort.Observation(123, NOW.minusSeconds(30), new AppIdentifier(TargetPlatform.LINUX, "/usr/bin/chat", "chat"));
    }
    private static FocusSession running() {
        var session = FocusSessionFactory.create(new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(60),
                Duration.ofSeconds(120), 4, false, false), at(0));
        session.start(at(0)); return session;
    }
    private static TimeStamp at(long seconds) { return new TimeStamp(Instant.parse("2026-11-26T00:00:00Z").plusSeconds(seconds)); }
}
