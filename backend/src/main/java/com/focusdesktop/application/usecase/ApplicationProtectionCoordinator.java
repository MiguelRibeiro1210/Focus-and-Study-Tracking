package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.*;
import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

/** Database transactions finish before native operations; a guardian owns restoration. */
@Service
public class ApplicationProtectionCoordinator {
    private final FocusSessionRepository sessions;
    private final BlockingPolicyRepository policies;
    private final ProtectionPreferences preferences;
    private final ApplicationSuspensionPort nativeApps;
    private final RecordDistractionEventUseCase events;
    private final Clock clock;
    private final boolean managed;
    private final Map<ActiveApplicationPort.Observation, Owned> owned = new LinkedHashMap<>();
    private final Map<DetectionKey, Instant> seen = new LinkedHashMap<>();
    private boolean recovered;
    private Instant nextRecoveryAttempt = Instant.EPOCH;
    private boolean enabled;
    private boolean unavailable;
    private boolean restorationPending;
    private Notice notice;

    public ApplicationProtectionCoordinator(FocusSessionRepository sessions, BlockingPolicyRepository policies,
            ProtectionPreferences preferences, ApplicationSuspensionPort nativeApps, RecordDistractionEventUseCase events,
            Clock clock, @Value("${focusdesktop.blocking.monitor-enabled:false}") boolean managed) {
        this.sessions = sessions; this.policies = policies; this.preferences = preferences; this.nativeApps = nativeApps;
        this.events = events; this.clock = clock; this.managed = managed;
    }

    public synchronized boolean allowsObservation() {
        return managed && recovered && !restorationPending && preferences.get().applicationsEnabled();
    }

    public synchronized void observe(DetectActiveApplicationUseCase.DetectedApplication candidate) {
        if (!managed || restorationPending) return;
        DistractionDetected detection = null;
        try {
            var context = current();
            if (context.isEmpty() || !sameInterval(candidate, context.get().session())) return;
            enabled = true;
            var observation = ObservedDistraction.application(candidate.observation().identity());
            var decision = context.get().policy().evaluate(observation);
            if (decision.isEmpty()) return;
            BlockingDecision selected = decision.get();
            if (owned.containsKey(candidate.observation())) return;
            var key = new DetectionKey(candidate.sessionId(), candidate.intervalId(), candidate.observation(), selected);
            if (selected.action() != BlockingAction.BLOCK && seen.containsKey(key)
                    && seen.get(key).plusSeconds(30).isAfter(clock.instant())) return;
            detection = DistractionDetected.capture(candidate.sessionId(), selected, observation, at());
            events.execute(detection);
            seen.put(key, clock.instant());
            if (seen.size() > 256) seen.remove(seen.keySet().iterator().next());
            if (selected.action() == BlockingAction.LOG) return;
            if (selected.action() == BlockingAction.WARN) { feedback("WARN", "Distraction detected: return to your focus task."); return; }
            if (!stillSelected(candidate, selected)) {
                events.execute(DistractionBlockFailed.from(detection, "Protection ended or the rule changed before suspension.", at()));
                return;
            }
            nativeApps.suspend(candidate.observation());
            // Track ownership before the final read so failure can always trigger release.
            owned.put(candidate.observation(), new Owned(candidate, selected));
            if (!stillSelected(candidate, selected)) {
                release(candidate.observation());
                events.execute(DistractionBlockFailed.from(detection, "Protection ended or the rule changed before suspension completed.", at()));
                return;
            }
            events.execute(DistractionBlocked.from(detection, at()));
            unavailable = false;
        } catch (RuntimeException exception) {
            String reason = exception instanceof ActiveApplicationPort.Unavailable ? exception.getMessage()
                    : "Application suspension failed; check permissions and desktop support.";
            unavailable = true;
            try { nativeApps.resume(candidate.observation()); owned.remove(candidate.observation()); }
            catch (RuntimeException restoreFailure) { restorationPending = true; }
            if (detection != null && detection.decision().action() == BlockingAction.BLOCK) {
                try { events.execute(DistractionBlockFailed.from(detection, reason, at())); } catch (RuntimeException ignored) { }
            }
            feedback("ERROR", restorationPending ? "Application restoration is pending. Keep FocusDesktop open while it retries recovery." : reason);
        }
    }

    public synchronized void reconcile() {
        if (!managed) return;
        try {
            if (!recovered) {
                if (nextRecoveryAttempt.isAfter(clock.instant())) return;
                nextRecoveryAttempt = clock.instant().plusSeconds(5);
                nativeApps.recover(); recovered = true;
            }
            if (restorationPending) { nativeApps.releaseAll(); owned.clear(); restorationPending = false; }
            enabled = preferences.get().applicationsEnabled();
            var context = current();
            for (var observation : List.copyOf(owned.keySet())) {
                Owned restriction = owned.get(observation);
                boolean keep = context.isPresent() && sameInterval(restriction.candidate(), context.get().session())
                        && context.get().policy().evaluate(ObservedDistraction.application(observation.identity()))
                        .filter(restriction.decision()::equals).filter(decision -> decision.action() == BlockingAction.BLOCK).isPresent();
                if (!keep) release(observation);
                else if (!nativeApps.isSuspended(observation)) {
                    release(observation);
                    feedback("ERROR", "An application restriction ended; protection will recheck matching foreground apps.");
                }
            }
            unavailable = false;
        } catch (RuntimeException exception) {
            unavailable = true;
            try { nativeApps.releaseAll(); owned.clear(); restorationPending = false; }
            catch (RuntimeException ignored) { restorationPending = true; }
            feedback("ERROR", exception instanceof ActiveApplicationPort.Unavailable ? exception.getMessage()
                    : "Protection is unavailable; owned application restrictions are being restored.");
        }
    }

    public synchronized Status status() {
        return new Status(enabled, owned.size(), restorationPending ? "RESTORING" : unavailable ? "UNAVAILABLE" : !enabled ? "DISABLED" : owned.isEmpty() ? "READY" : "ACTIVE", notice);
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (!managed) return;
        try { nativeApps.releaseAll(); owned.clear(); } catch (RuntimeException ignored) { }
    }

    private Optional<Context> current() {
        if (!preferences.get().applicationsEnabled()) return Optional.empty();
        return sessions.findActive().filter(session -> session.state() == SessionState.RUNNING
                && session.currentInterval().type() == SessionType.WORK && session.currentInterval().state() == IntervalState.RUNNING
                && session.currentInterval().remainingDuration(at()).seconds() > 0)
                .flatMap(session -> policies.findActive().map(policy -> new Context(session, policy)));
    }
    private boolean stillSelected(DetectActiveApplicationUseCase.DetectedApplication candidate, BlockingDecision decision) {
        return current().filter(context -> sameInterval(candidate, context.session()))
                .flatMap(context -> context.policy().evaluate(ObservedDistraction.application(candidate.observation().identity())))
                .filter(decision::equals).isPresent();
    }
    private static boolean sameInterval(DetectActiveApplicationUseCase.DetectedApplication candidate, FocusSession session) {
        return candidate.sessionId().equals(session.id()) && candidate.intervalId().equals(session.currentInterval().id());
    }
    private void release(ActiveApplicationPort.Observation observation) { nativeApps.resume(observation); owned.remove(observation); }
    private void feedback(String kind, String message) {
        if (notice == null || !notice.message().equals(message) || notice.occurredAt().plusSeconds(10).isBefore(clock.instant()))
            notice = new Notice(UUID.randomUUID(), kind, message, clock.instant());
    }
    private TimeStamp at() { return new TimeStamp(clock.instant()); }
    private record Context(FocusSession session, BlockingPolicy policy) { }
    private record Owned(DetectActiveApplicationUseCase.DetectedApplication candidate, BlockingDecision decision) { }
    private record DetectionKey(SessionId session, IntervalId interval, ActiveApplicationPort.Observation observation, BlockingDecision decision) { }
    public record Notice(UUID id, String kind, String message, Instant occurredAt) { }
    public record Status(boolean applicationProtectionEnabled, int ownedRestrictions, String applicationProtectionState, Notice notice) { }
}
