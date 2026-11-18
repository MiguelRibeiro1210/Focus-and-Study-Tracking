package com.focusdesktop.application.dto;

import com.focusdesktop.domain.model.SessionSettings;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataExportBundle(SettingsDto settings, List<SessionExport> sessions, AnalyticsSummary analytics) {
    public DataExportBundle {
        sessions = List.copyOf(sessions);
    }

    public record SessionExport(UUID sessionId, String projectName, String sessionType, String status,
                                Instant createdAt, Instant startedAt, Instant endedAt,
                                SessionSettings settingsSnapshot, List<IntervalExport> intervals) {
        public SessionExport { intervals = List.copyOf(intervals); }
    }

    public record IntervalExport(UUID intervalId, int sequence, String type, String state,
                                 long plannedSeconds, long actualSeconds, long remainingSeconds,
                                 Instant startedAt, Instant endedAt, Instant pausedAt) { }
}
