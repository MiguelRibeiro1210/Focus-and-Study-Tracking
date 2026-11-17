package com.focusdesktop.application.dto;

import java.time.LocalDate;

public record AnalyticsDailySummary(LocalDate date, long focusSeconds, long breakSeconds,
                                    long completedSessions) { }
