package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.BlockingDecision;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;
import java.util.UUID;

/** Construct only after an enforcement adapter confirms success. */
public record DistractionBlocked(UUID eventId, UUID detectionId, SessionId sessionId,
                                 BlockingDecision decision, ObservedDistraction observation,
                                 TimeStamp occurredAt) implements DistractionBlockOutcome {
    public DistractionBlocked {
        DistractionDetected.validateFacts(eventId, detectionId, sessionId, decision, observation, occurredAt);
        DistractionDetected.requireBlock(decision);
    }

    public static DistractionBlocked from(DistractionDetected detection, TimeStamp occurredAt) {
        Objects.requireNonNull(detection, "detection").validateOutcomeAt(occurredAt);
        return new DistractionBlocked(UUID.randomUUID(), detection.detectionId(), detection.sessionId(),
                detection.decision(), detection.observation(), occurredAt);
    }
}
