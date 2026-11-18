package com.focusdesktop.domain.model;

public record DailyFocusGoal(int hours) {
    public DailyFocusGoal {
        if (hours < 1 || hours > 24) {
            throw new IllegalArgumentException("Daily focus goal must be between 1 and 24 whole hours");
        }
    }

    public long seconds() {
        return hours * 3600L;
    }
}
