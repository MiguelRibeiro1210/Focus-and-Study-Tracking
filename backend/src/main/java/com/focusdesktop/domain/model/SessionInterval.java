package com.focusdesktop.domain.model;

import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;

public final class SessionInterval {
    private final IntervalId id;
    private final int sequenceNumber;
    private final SessionType type;
    private final Duration plannedDuration;
    private IntervalState state;
    private Duration actualDuration;
    private Duration remainingDuration;
    private TimeStamp startedAt;
    private TimeStamp endedAt;
    private TimeStamp pausedAt;

    public SessionInterval(IntervalId id, int sequenceNumber, SessionType type,
                           Duration plannedDuration, Duration actualDuration, Duration remainingDuration,
                           IntervalState state, TimeStamp startedAt, TimeStamp endedAt, TimeStamp pausedAt) {
        this.id = java.util.Objects.requireNonNull(id, "id");
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        this.sequenceNumber = sequenceNumber;
        this.type = java.util.Objects.requireNonNull(type, "type");
        this.plannedDuration = java.util.Objects.requireNonNull(plannedDuration, "plannedDuration");
        if (plannedDuration.seconds() == 0) {
            throw new IllegalArgumentException("plannedDuration must be positive");
        }
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.actualDuration = java.util.Objects.requireNonNull(actualDuration, "actualDuration");
        this.remainingDuration = java.util.Objects.requireNonNull(remainingDuration, "remainingDuration");
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.pausedAt = pausedAt;
    }

    void start(TimeStamp now) {
        java.util.Objects.requireNonNull(now, "now");
        if (state != IntervalState.PENDING) {
            throw new IllegalStateException("Only a pending interval can be started");
        }
        state = IntervalState.RUNNING;
        startedAt = now;
        remainingDuration = plannedDuration;
    }

    void pause(TimeStamp now) {
        java.util.Objects.requireNonNull(now, "now");
        requireRunningSegment();
        long elapsed = startedAt.secondsUntil(now);
        actualDuration = Duration.ofSeconds(Math.addExact(actualDuration.seconds(), elapsed));
        remainingDuration = Duration.ofSeconds(Math.max(0, plannedDuration.seconds() - actualDuration.seconds()));
        pausedAt = now;
    }

    void resume(TimeStamp now) {
        java.util.Objects.requireNonNull(now, "now");
        if (state != IntervalState.RUNNING || pausedAt == null) {
            throw new IllegalStateException("Only a paused interval can be resumed");
        }
        pausedAt.secondsUntil(now);
        startedAt = now;
        pausedAt = null;
    }

    void complete(TimeStamp now) {
        java.util.Objects.requireNonNull(now, "now");
        if (state != IntervalState.RUNNING) {
            throw new IllegalStateException("Only a running interval can be completed");
        }
        if (pausedAt == null) {
            actualDuration = Duration.ofSeconds(Math.addExact(actualDuration.seconds(), startedAt.secondsUntil(now)));
        } else {
            pausedAt.secondsUntil(now);
        }
        endedAt = now;
        pausedAt = null;
        remainingDuration = Duration.ofSeconds(Math.max(0, plannedDuration.seconds() - actualDuration.seconds()));
        state = IntervalState.COMPLETED;
    }

    void skip(TimeStamp now) {
        java.util.Objects.requireNonNull(now, "now");
        if (type == SessionType.WORK || state != IntervalState.PENDING) {
            throw new IllegalStateException("Only a pending break interval can be skipped");
        }
        endedAt = now;
        state = IntervalState.SKIPPED;
        remainingDuration = Duration.ofSeconds(0);
    }

    private void requireRunningSegment() {
        if (state != IntervalState.RUNNING || pausedAt != null || startedAt == null) {
            throw new IllegalStateException("Interval has no running segment");
        }
    }

    public IntervalId id() { return id; }
    public int sequenceNumber() { return sequenceNumber; }
    public SessionType type() { return type; }
    public Duration plannedDuration() { return plannedDuration; }
    public IntervalState state() { return state; }
    public Duration actualDuration() { return actualDuration; }
    public Duration remainingDuration() { return remainingDuration; }
    public Duration actualDuration(TimeStamp asOf) {
        java.util.Objects.requireNonNull(asOf, "asOf");
        if (state != IntervalState.RUNNING || pausedAt != null) {
            return actualDuration;
        }
        return Duration.ofSeconds(Math.addExact(actualDuration.seconds(), startedAt.secondsUntil(asOf)));
    }
    public Duration remainingDuration(TimeStamp asOf) {
        long elapsed = actualDuration(asOf).seconds();
        return Duration.ofSeconds(Math.max(0, plannedDuration.seconds() - elapsed));
    }
    public TimeStamp startedAt() { return startedAt; }
    public TimeStamp endedAt() { return endedAt; }
    public TimeStamp pausedAt() { return pausedAt; }
    public boolean isPaused() { return state == IntervalState.RUNNING && pausedAt != null; }

    public SessionIntervalSnapshot snapshot() {
        return new SessionIntervalSnapshot(id, sequenceNumber, type, plannedDuration, actualDuration,
                remainingDuration, state, startedAt, endedAt, pausedAt);
    }
}
