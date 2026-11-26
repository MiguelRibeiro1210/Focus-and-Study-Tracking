package com.focusdesktop.application.dto;

import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.model.*;
import java.time.Instant;
import java.util.UUID;

/** Versioned wire snapshot; native PIDs and handles are deliberately excluded. */
public record DistractionEventPayload(int version, UUID eventId, UUID detectionId, UUID sessionId,
        UUID ruleId, BlockingRuleDto.Target target, DistractionCategory category, BlockingAction action,
        String observationType, AppIdentifier application, String host, String outcome, String failureReason, Instant occurredAt) {
    public DomainEvent toDomainEvent() {
        if (version != 1) throw new IllegalArgumentException("Unsupported distraction snapshot version");
        var decision = new BlockingDecision(new BlockingRuleId(ruleId), target.toDomain(), category, action);
        ObservedDistraction observation;
        if ("APPLICATION".equals(observationType) && application != null && host == null)
            observation = ObservedDistraction.application(application);
        else if ("WEBSITE".equals(observationType) && host != null && application == null)
            observation = ObservedDistraction.website(host);
        else throw new IllegalArgumentException("Invalid distraction observation snapshot");
        var session = new SessionId(sessionId);
        var at = new TimeStamp(occurredAt);
        return switch (outcome) {
            case "PENDING" -> {
                if (failureReason != null) throw new IllegalArgumentException("Detection cannot have a failure reason");
                yield new DistractionDetected(eventId, detectionId, session, decision, observation, at);
            }
            case "BLOCKED" -> {
                if (failureReason != null) throw new IllegalArgumentException("Success cannot have a failure reason");
                yield new DistractionBlocked(eventId, detectionId, session, decision, observation, at);
            }
            case "FAILED" -> new DistractionBlockFailed(eventId, detectionId, session, decision, observation, failureReason, at);
            default -> throw new IllegalArgumentException("Unsupported distraction outcome");
        };
    }
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
