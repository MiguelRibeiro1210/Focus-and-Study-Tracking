package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.BlockingDecision;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionId;
import java.util.UUID;

/** Correlated enforcement fact, not a command to repeat an OS/browser operation. */
public sealed interface DistractionBlockOutcome extends DomainEvent
        permits DistractionBlocked, DistractionBlockFailed {
    UUID eventId();
    UUID detectionId();
    SessionId sessionId();
    BlockingDecision decision();
    ObservedDistraction observation();
}
