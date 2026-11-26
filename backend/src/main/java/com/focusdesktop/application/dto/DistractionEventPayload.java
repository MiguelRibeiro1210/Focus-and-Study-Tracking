package com.focusdesktop.application.dto;

import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.model.*;
import java.time.Instant;
import java.util.UUID;

/** Versioned wire snapshot; native PIDs and handles are deliberately excluded. */
public record DistractionEventPayload(int version, UUID eventId, UUID detectionId, UUID sessionId,
        UUID ruleId, BlockingRuleDto.Target target, DistractionCategory category, BlockingAction action,
        String observationType, AppIdentifier application, String host, String outcome, String failureReason, Instant occurredAt) {
    public static DistractionEventPayload from(DomainEvent event) {
        if (event instanceof DistractionDetected detection) return snapshot(detection.eventId(), detection.detectionId(),
                detection.sessionId(), detection.decision(), detection.observation(), "PENDING", null, detection.occurredAt());
        if (event instanceof DistractionBlocked blocked) return snapshot(blocked.eventId(), blocked.detectionId(),
                blocked.sessionId(), blocked.decision(), blocked.observation(), "BLOCKED", null, blocked.occurredAt());
        if (event instanceof DistractionBlockFailed failed) return snapshot(failed.eventId(), failed.detectionId(),
                failed.sessionId(), failed.decision(), failed.observation(), "FAILED", failed.reason(), failed.occurredAt());
        throw new IllegalArgumentException("Not a distraction event");
    }
    private static DistractionEventPayload snapshot(UUID eventId, UUID detectionId, SessionId sessionId,
            BlockingDecision decision, ObservedDistraction observation, String outcome, String failure, TimeStamp at) {
        var app = observation instanceof ObservedDistraction.Application application ? application.identifier() : null;
        String host = observation instanceof ObservedDistraction.Website website ? website.host() : null;
        return new DistractionEventPayload(1, eventId, detectionId, sessionId.value(), decision.ruleId().value(),
                BlockingRuleDto.Target.from(decision.target()), decision.category(), decision.action(),
                app == null ? "WEBSITE" : "APPLICATION", app, host, outcome, failure, at.value());
    }
}
