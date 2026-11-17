package com.focusdesktop.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessionHistoryItem(
        UUID sessionId,
        String projectName,
        String sessionType,
        String status,
        Instant startedAt,
        Instant endedAt,
        long actualFocusDurationSeconds) {
    public SessionHistoryItem {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(sessionType, "sessionType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
    }
}
