package com.focusdesktop.application.dto;

import java.time.LocalDate;

public record AnalyticsBestDay(LocalDate date, long focusSeconds, long completedSessions) { }
