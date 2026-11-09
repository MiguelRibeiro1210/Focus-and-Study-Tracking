package com.focusdesktop.domain.model;

public enum SessionState {
    IDLE,
    RUNNING,
    PAUSED,
    WAITING_FOR_NEXT_INTERVAL,
    COMPLETED,
    ABANDONED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED;
    }

    public boolean isActive() {
        return this == RUNNING || this == PAUSED || this == WAITING_FOR_NEXT_INTERVAL;
    }
}
