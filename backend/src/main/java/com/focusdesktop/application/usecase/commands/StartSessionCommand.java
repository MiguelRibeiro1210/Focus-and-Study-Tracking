package com.focusdesktop.application.usecase.commands;

import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.SessionSettings;
import java.util.Objects;

public record StartSessionCommand(String projectName, SessionType sessionType, TimerOverride timerOverride) {
    public StartSessionCommand(String projectName, SessionType sessionType) {
        this(projectName, sessionType, null);
    }

    public StartSessionCommand {
        Objects.requireNonNull(projectName, "projectName");
        projectName = projectName.trim();
        if (projectName.isEmpty() || projectName.length() > 120) {
            throw new IllegalArgumentException("projectName must contain between 1 and 120 characters");
        }
        Objects.requireNonNull(sessionType, "sessionType");
    }

    public record TimerOverride(Long workSeconds, Long shortBreakSeconds, Long longBreakSeconds) {
        public SessionSettings apply(SessionSettings defaults) {
            Objects.requireNonNull(defaults, "defaults");
            return new SessionSettings(
                    workSeconds == null ? defaults.workDuration() : Duration.ofSeconds(workSeconds),
                    shortBreakSeconds == null ? defaults.shortBreakDuration() : Duration.ofSeconds(shortBreakSeconds),
                    longBreakSeconds == null ? defaults.longBreakDuration() : Duration.ofSeconds(longBreakSeconds),
                    defaults.intervalsBeforeLongBreak(), defaults.autoStartBreaks(), defaults.autoStartWork());
        }
    }
}
