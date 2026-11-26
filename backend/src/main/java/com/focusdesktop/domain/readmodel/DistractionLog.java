package com.focusdesktop.domain.readmodel;

import com.focusdesktop.domain.event.DistractionBlockFailed;
import com.focusdesktop.domain.event.DistractionBlockOutcome;
import com.focusdesktop.domain.event.DistractionBlocked;
import com.focusdesktop.domain.event.DistractionDetected;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable review view of a detection and optional confirmed outcome; projection/storage is FST-037. */
public record DistractionLog(DistractionDetected detection, Optional<DistractionBlockOutcome> outcome) {
    public DistractionLog {
        Objects.requireNonNull(detection, "detection");
        Objects.requireNonNull(outcome, "outcome");
        outcome.ifPresent(result -> {
            if (!result.detectionId().equals(detection.detectionId())
                    || !result.sessionId().equals(detection.sessionId())
                    || !result.decision().equals(detection.decision())
                    || !result.observation().equals(detection.observation())) {
                throw new IllegalArgumentException("Log outcome must describe the same detection facts");
            }
            detection.occurredAt().secondsUntil(result.occurredAt());
        });
    }

    public UUID detectionId() { return detection.detectionId(); }

    public boolean blocked() { return outcome.filter(DistractionBlocked.class::isInstance).isPresent(); }

    public Optional<String> failureReason() {
        return outcome.filter(DistractionBlockFailed.class::isInstance)
                .map(DistractionBlockFailed.class::cast).map(DistractionBlockFailed::reason);
    }
}
