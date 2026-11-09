package com.focusdesktop.domain.model;

public record Duration(long seconds) {
    public Duration {
        if (seconds < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
    }

    public static Duration ofSeconds(long seconds) {
        return new Duration(seconds);
    }
}
