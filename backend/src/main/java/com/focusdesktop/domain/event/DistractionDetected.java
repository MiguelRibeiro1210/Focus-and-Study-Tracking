package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.BlockingAction;
import com.focusdesktop.domain.model.BlockingDecision;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;
import java.util.UUID;

public record DistractionDetected(UUID eventId, UUID detectionId, SessionId sessionId,
                                  BlockingDecision decision, ObservedDistraction observation,
                                  TimeStamp occurredAt) implements DomainEvent {
    public DistractionDetected {
        validateFacts(eventId, detectionId, sessionId, decision, observation, occurredAt);
    }

    public static DistractionDetected capture(SessionId sessionId, BlockingDecision decision,
                                              ObservedDistraction observation, TimeStamp occurredAt) {
        return new DistractionDetected(UUID.randomUUID(), UUID.randomUUID(), sessionId,
                decision, observation, occurredAt);
    }

    static void validateFacts(UUID eventId, UUID detectionId, SessionId sessionId,
                              BlockingDecision decision, ObservedDistraction observation, TimeStamp occurredAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(detectionId, "detectionId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!decision.target().matches(observation)) {
            throw new IllegalArgumentException("Detection observation must match the selected target");
        }
    }

    static void requireBlock(BlockingDecision decision) {
        if (decision.action() != BlockingAction.BLOCK) {
            throw new IllegalArgumentException("A block outcome requires a BLOCK decision");
        }
    }

    void validateOutcomeAt(TimeStamp occurredAt) {
        requireBlock(decision);
        this.occurredAt.secondsUntil(occurredAt);
    }
}
