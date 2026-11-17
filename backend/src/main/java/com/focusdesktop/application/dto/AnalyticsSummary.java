package com.focusdesktop.application.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsSummary(LocalDate from, LocalDate to, long totalFocusSeconds, long totalBreakSeconds,
                               long completedSessions, long averageSessionSeconds, int focusScore,
                               AnalyticsBestDay bestDay, List<AnalyticsDailySummary> daily) {
    public AnalyticsSummary {
        daily = List.copyOf(daily);
    }
}
