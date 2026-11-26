package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.BlockingDecision;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import java.util.Objects;
import java.util.UUID;

public record DistractionBlockFailed(UUID eventId, UUID detectionId, SessionId sessionId,
                                     BlockingDecision decision, ObservedDistraction observation,
                                     String reason, TimeStamp occurredAt) implements DistractionBlockOutcome {
    public DistractionBlockFailed {
        DistractionDetected.validateFacts(eventId, detectionId, sessionId, decision, observation, occurredAt);
        DistractionDetected.requireBlock(decision);
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("A failed block needs a reason");
    }

    public static DistractionBlockFailed from(DistractionDetected detection, String reason, TimeStamp occurredAt) {
        Objects.requireNonNull(detection, "detection").validateOutcomeAt(occurredAt);
        return new DistractionBlockFailed(UUID.randomUUID(), detection.detectionId(), detection.sessionId(),
                detection.decision(), detection.observation(), reason, occurredAt);
    }
}
