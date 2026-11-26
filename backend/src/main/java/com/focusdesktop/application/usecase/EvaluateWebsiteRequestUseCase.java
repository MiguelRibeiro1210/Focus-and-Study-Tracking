package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Stores host-only observations; transport confirms denial before completing an outcome. */
@Service
public class EvaluateWebsiteRequestUseCase {
    public record Decision(boolean block, Runnable denied, Runnable failed) {
        public static Decision allow() { return new Decision(false, () -> {}, () -> {}); }
    }
    private final FocusSessionRepository sessions;
    private final BlockingPolicyRepository policies;
    private final WebsiteProtectionPreferences preferences;
    private final RecordDistractionEventUseCase events;
    private final Clock clock;
    private final Map<Key, Instant> seen = new LinkedHashMap<>();
    private volatile ApplicationProtectionCoordinator.Notice notice;
    public EvaluateWebsiteRequestUseCase(FocusSessionRepository sessions, BlockingPolicyRepository policies,
            WebsiteProtectionPreferences preferences, RecordDistractionEventUseCase events, Clock clock) {
        this.sessions = sessions; this.policies = policies; this.preferences = preferences; this.events = events; this.clock = clock;
    }
    public synchronized Decision execute(String host) {
        try {
            var context = current(host);
            if (context.isEmpty()) return Decision.allow();
            var selected = context.get();
            var key = new Key(selected.session(), selected.interval(), host, selected.decision());
            boolean block = selected.decision().action() == BlockingAction.BLOCK;
            if (seen.getOrDefault(key, Instant.EPOCH).plusSeconds(30).isAfter(clock.instant()))
                return new Decision(block, () -> {}, () -> {});
            var detection = DistractionDetected.capture(selected.session(), selected.decision(), ObservedDistraction.website(host), at());
            events.execute(detection);
            if (!current(host).equals(context)) return Decision.allow();
            seen.put(key, clock.instant());
            if (seen.size() > 256) seen.remove(seen.keySet().iterator().next());
            if (selected.decision().action() == BlockingAction.WARN)
                feedback("WARN", "Website distraction detected: return to your focus task.");
            var completed = new java.util.concurrent.atomic.AtomicBoolean();
            return new Decision(block,
                    () -> { if (completed.compareAndSet(false, true)) {
                        events.execute(DistractionBlocked.from(detection, at()));
                        feedback("WARN", "A website request was blocked during focus time.");
                    } },
                    () -> { if (completed.compareAndSet(false, true)) {
                        events.execute(DistractionBlockFailed.from(detection, "Website request denial could not be confirmed.", at()));
                        feedback("ERROR", "Website protection could not confirm a request denial.");
                    } });
        } catch (RuntimeException exception) {
            feedback("ERROR", "Website policy is unavailable; requests are being allowed.");
            return Decision.allow();
        }
    }
    public ApplicationProtectionCoordinator.Notice notice() { return notice; }
    private Optional<Context> current(String host) {
        if (!preferences.get().enabled()) return Optional.empty();
        return sessions.findActive().filter(session -> session.state() == SessionState.RUNNING
                && session.currentInterval().type() == SessionType.WORK
                && session.currentInterval().state() == IntervalState.RUNNING
                && session.currentInterval().remainingDuration(at()).seconds() > 0)
                .flatMap(session -> policies.findActive().flatMap(policy -> policy.evaluate(ObservedDistraction.website(host)))
                        .map(decision -> new Context(session.id(), session.currentInterval().id(), decision)));
    }
    private TimeStamp at() { return new TimeStamp(clock.instant()); }
    private void feedback(String kind, String message) {
        notice = new ApplicationProtectionCoordinator.Notice(UUID.randomUUID(), kind, message, clock.instant());
    }
    private record Context(SessionId session, IntervalId interval, BlockingDecision decision) { }
    private record Key(SessionId session, IntervalId interval, String host, BlockingDecision decision) { }
}
