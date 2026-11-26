package com.focusdesktop.application.dto;

import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.readmodel.DistractionLog;
import java.time.Instant;
import java.util.*;

public record DistractionLogDto(UUID detectionId, UUID sessionId, UUID ruleId, BlockingRuleDto.Target target,
        String observationType, AppIdentifier application, String host, DistractionCategory category,
        BlockingAction action, Instant occurredAt, String outcome, Instant outcomeAt, boolean blocked, String failureReason) {
    public static DistractionLogDto from(DistractionEventPayload detected, DistractionEventPayload result) {
        var detection = (DistractionDetected) detected.toDomainEvent();
        var outcome = result == null ? Optional.<DistractionBlockOutcome>empty()
                : Optional.of((DistractionBlockOutcome) result.toDomainEvent());
        var validated = new DistractionLog(detection, outcome);
        return new DistractionLogDto(detected.detectionId(), detected.sessionId(), detected.ruleId(), detected.target(),
                detected.observationType(), detected.application(), detected.host(), detected.category(), detected.action(),
                detected.occurredAt(), result == null ? "PENDING" : result.outcome(), result == null ? null : result.occurredAt(),
                validated.blocked(), validated.failureReason().orElse(null));
    }
}
