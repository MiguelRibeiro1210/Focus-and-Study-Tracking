package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import java.time.Instant;
import java.util.UUID;

public record SessionHistoryJpaRow(UUID sessionId, String projectName, SessionType sessionType,
                                   SessionState status, Instant startedAt, Instant endedAt,
                                   long actualFocusDurationSeconds) { }
