package com.focusdesktop.domain.model;

import java.util.Objects;

public record SessionSettings(
        Duration workDuration,
        Duration shortBreakDuration,
        Duration longBreakDuration,
        int intervalsBeforeLongBreak, //Number of work intervals before a long break
        boolean autoStartBreaks,
        boolean autoStartWork) {

    public SessionSettings {
        Objects.requireNonNull(workDuration, "workDuration");
        Objects.requireNonNull(shortBreakDuration, "shortBreakDuration");
        Objects.requireNonNull(longBreakDuration, "longBreakDuration");
        requireMinutes(workDuration, 1, 180, "workDuration");
        requireMinutes(shortBreakDuration, 1, 60, "shortBreakDuration");
        requireMinutes(longBreakDuration, 1, 120, "longBreakDuration");
        if (longBreakDuration.seconds() < shortBreakDuration.seconds()) {
            throw new IllegalArgumentException("Long break cannot be shorter than short break");
        }
        if (intervalsBeforeLongBreak < 1 || intervalsBeforeLongBreak > 12) {
            throw new IllegalArgumentException("intervalsBeforeLongBreak must be between 1 and 12");
        }
    }

    private static void requireMinutes(Duration duration, int min, int max, String name) {
        long seconds = duration.seconds();
        if (seconds % 60 != 0 || seconds < min * 60L || seconds > max * 60L) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max
                    + " whole minutes");
        }
    }
}
