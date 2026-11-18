package com.focusdesktop.domain.model;

import java.util.Objects;

public record TimerPreferences(
        Duration workDuration,
        Duration shortBreakDuration,
        Duration longBreakDuration,
        int intervalsBeforeLongBreak,
        boolean autoStartBreaks,
        boolean autoStartWork) {

    public TimerPreferences {
        Objects.requireNonNull(workDuration, "workDuration");
        Objects.requireNonNull(shortBreakDuration, "shortBreakDuration");
        Objects.requireNonNull(longBreakDuration, "longBreakDuration");
        new SessionSettings(workDuration, shortBreakDuration, longBreakDuration,
                intervalsBeforeLongBreak, autoStartBreaks, autoStartWork);
    }

    public static TimerPreferences from(SessionSettings settings) {
        return new TimerPreferences(settings.workDuration(), settings.shortBreakDuration(),
                settings.longBreakDuration(), settings.intervalsBeforeLongBreak(),
                settings.autoStartBreaks(), settings.autoStartWork());
    }

    public SessionSettings toSessionSettings() {
        return new SessionSettings(workDuration, shortBreakDuration, longBreakDuration,
                intervalsBeforeLongBreak, autoStartBreaks, autoStartWork);
    }
}
