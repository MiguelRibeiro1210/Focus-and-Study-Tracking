package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.ActiveApplicationPort;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DetectActiveApplicationUseCase {
    private final FocusSessionRepository sessions;
    private final ActiveApplicationPort applications;
    private final Clock clock;
    private final boolean enabled;
    private volatile Status status;

    public DetectActiveApplicationUseCase(FocusSessionRepository sessions, ActiveApplicationPort applications, Clock clock,
            @Value("${focusdesktop.blocking.monitor-enabled:false}") boolean enabled) {
        this.sessions = sessions; this.applications = applications; this.clock = clock; this.enabled = enabled;
        update(enabled ? "IDLE" : "DISABLED", enabled ? "Waiting for a running work interval." : "Application monitoring is disabled.");
    }

    public Optional<DetectedApplication> execute() {
        if (!enabled) return Optional.empty();
        try {
            var before = sessions.findActive().filter(this::eligible);
            if (before.isEmpty()) { idle(); return Optional.empty(); }
            var sessionId = before.get().id();
            var intervalId = before.get().currentInterval().id();
            Optional<ActiveApplicationPort.Observation> observation = applications.currentApplication();
            var after = sessions.findActive().filter(this::eligible)
                    .filter(session -> session.id().equals(sessionId) && session.currentInterval().id().equals(intervalId));
            if (after.isEmpty()) { idle(); return Optional.empty(); }
            update("OBSERVING", observation.isPresent() ? "Foreground application detection is available."
                    : "No foreground application is currently available.");
            return observation.map(value -> new DetectedApplication(sessionId, intervalId, value, clock.instant()));
        } catch (ActiveApplicationPort.Unavailable exception) {
            update("UNAVAILABLE", exception.getMessage()); return Optional.empty();
        } catch (RuntimeException exception) {
            update("UNAVAILABLE", "Application detection failed; check desktop access and permissions.");
            return Optional.empty();
        }
    }

    public Status status() { return status; }

    private boolean eligible(FocusSession session) {
        return session.state() == SessionState.RUNNING && session.currentInterval().type() == SessionType.WORK
                && session.currentInterval().state() == IntervalState.RUNNING
                && session.currentInterval().remainingDuration(new TimeStamp(clock.instant())).seconds() > 0;
    }
    private void idle() { update("IDLE", "Monitoring is idle outside running work intervals."); }
    private void update(String state, String reason) { status = new Status(enabled, state, reason, false, false, clock.instant()); }

    public record DetectedApplication(SessionId sessionId, IntervalId intervalId,
                                     ActiveApplicationPort.Observation observation, Instant observedAt) { }
    public record Status(boolean monitorEnabled, String applicationDetection, String applicationReason,
                         boolean applicationEnforcement, boolean websiteEnforcement, Instant checkedAt) { }
}
