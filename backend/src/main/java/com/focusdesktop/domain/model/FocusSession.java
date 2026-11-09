package com.focusdesktop.domain.model;

import com.focusdesktop.domain.factories.SessionIntervalFactory;
import com.focusdesktop.domain.factories.snapshots.FocusSessionSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Aggregate root for a session and its ordered work and break intervals. */
public final class FocusSession {
    private static final long MINIMUM_FOCUS_SECONDS_FOR_COMPLETION = 5 * 60L;

    private final SessionId id;
    private final SessionType sessionType;
    private final SessionSettings settings;
    private final TimeStamp createdAt;
    private final List<SessionInterval> intervals;
    private SessionState state;
    private TimeStamp startedAt;
    private TimeStamp endedAt;

    public FocusSession(SessionId id, SessionType sessionType, SessionSettings settings,
                        TimeStamp createdAt, SessionState state, TimeStamp startedAt,
                        TimeStamp endedAt, List<SessionInterval> intervals) {
        this.id = Objects.requireNonNull(id, "id");
        this.sessionType = Objects.requireNonNull(sessionType, "sessionType");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.state = Objects.requireNonNull(state, "state");
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.intervals = new ArrayList<>(Objects.requireNonNull(intervals, "intervals"));
    }

    public void start(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireState(SessionState.IDLE);
        createdAt.secondsUntil(now);
        if (!intervals.isEmpty()) {
            throw new IllegalStateException("Idle session already contains intervals");
        }
        startedAt = now;
        intervals.add(SessionIntervalFactory.createPending(1, SessionType.WORK, settings.workDuration()));
        intervals.get(0).start(now);
        state = SessionState.RUNNING;
    }

    public void pause(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireState(SessionState.RUNNING);
        currentInterval().pause(now);
        state = SessionState.PAUSED;
    }

    public void resume(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireState(SessionState.PAUSED);
        currentInterval().resume(now);
        state = SessionState.RUNNING;
    }

    public void completeInterval(SessionType nextType, TimeStamp now) {
        Objects.requireNonNull(nextType, "nextType");
        Objects.requireNonNull(now, "now");
        requireState(SessionState.RUNNING);
        SessionType currentType = currentInterval().type();
        boolean validNextType = currentType == SessionType.WORK
                ? nextType != SessionType.WORK
                : nextType == SessionType.WORK;
        if (!validNextType) {
            throw new IllegalArgumentException("Work intervals must be followed by a break and breaks by work");
        }
        currentInterval().complete(now);
        SessionInterval next = newInterval(nextType);
        intervals.add(next);
        if (autoStart(nextType)) {
            next.start(now);
            state = SessionState.RUNNING;
        } else {
            state = SessionState.WAITING_FOR_NEXT_INTERVAL;
        }
    }

    public void startNextInterval(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireState(SessionState.WAITING_FOR_NEXT_INTERVAL);
        previousIntervalEndedAt().secondsUntil(now);
        currentInterval().start(now);
        state = SessionState.RUNNING;
    }

    public void skipBreak(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireState(SessionState.WAITING_FOR_NEXT_INTERVAL);
        previousIntervalEndedAt().secondsUntil(now);
        currentInterval().skip(now);
        SessionInterval nextWork = newInterval(SessionType.WORK);
        intervals.add(nextWork);
        if (settings.autoStartWork()) {
            nextWork.start(now);
            state = SessionState.RUNNING;
        } else {
            state = SessionState.WAITING_FOR_NEXT_INTERVAL;
        }
    }

    public void stop(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireMutable();
        if (state == SessionState.IDLE) {
            throw new IllegalStateException("An idle session cannot be stopped");
        }
        startedAt.secondsUntil(now);
        if (state == SessionState.WAITING_FOR_NEXT_INTERVAL) {
            previousIntervalEndedAt().secondsUntil(now);
        }
        if (state == SessionState.RUNNING || state == SessionState.PAUSED) {
            currentInterval().complete(now);
        }
        if (startedAt == null) {
            throw new IllegalStateException("Started session has no start timestamp");
        }
        endedAt = now;
        state = actualFocusDuration().seconds() >= MINIMUM_FOCUS_SECONDS_FOR_COMPLETION
                ? SessionState.COMPLETED
                : SessionState.ABANDONED;
    }

    public void abandon(TimeStamp now) {
        Objects.requireNonNull(now, "now");
        requireMutable();
        if (state == SessionState.IDLE) {
            throw new IllegalStateException("An idle session cannot be abandoned");
        }
        startedAt.secondsUntil(now);
        if (state == SessionState.WAITING_FOR_NEXT_INTERVAL) {
            previousIntervalEndedAt().secondsUntil(now);
        }
        if (state == SessionState.RUNNING || state == SessionState.PAUSED) {
            currentInterval().complete(now);
        }
        endedAt = now;
        state = SessionState.ABANDONED;
    }

    private SessionInterval newInterval(SessionType type) {
        Duration duration = switch (type) {
            case WORK -> settings.workDuration();
            case SHORT_BREAK -> settings.shortBreakDuration();
            case LONG_BREAK -> settings.longBreakDuration();
        };
        return SessionIntervalFactory.createPending(intervals.size() + 1, type, duration);
    }

    private boolean autoStart(SessionType type) {
        return type == SessionType.WORK ? settings.autoStartWork() : settings.autoStartBreaks();
    }

    public SessionInterval currentInterval() {
        if (intervals.isEmpty()) {
            throw new IllegalStateException("Session has no interval");
        }
        return intervals.get(intervals.size() - 1);
    }

    private TimeStamp previousIntervalEndedAt() {
        if (intervals.size() < 2) {
            throw new IllegalStateException("Pending interval has no preceding interval");
        }
        TimeStamp ended = intervals.get(intervals.size() - 2).endedAt();
        if (ended == null) {
            throw new IllegalStateException("Preceding interval has not ended");
        }
        return ended;
    }

    private void requireState(SessionState expected) {
        requireMutable();
        if (state != expected) {
            throw new IllegalStateException("Expected session state " + expected + " but was " + state);
        }
    }

    private void requireMutable() {
        if (state.isTerminal()) {
            throw new IllegalStateException("Terminal sessions are immutable");
        }
    }

    public Duration actualFocusDuration() {
        long seconds = intervals.stream()
                .filter(interval -> interval.type() == SessionType.WORK)
                .mapToLong(interval -> interval.actualDuration().seconds())
                .sum();
        return Duration.ofSeconds(seconds);
    }

    public Duration actualFocusDuration(TimeStamp asOf) {
        Objects.requireNonNull(asOf, "asOf");
        long seconds = actualFocusDuration().seconds();
        if (state == SessionState.RUNNING && !intervals.isEmpty()) {
            SessionInterval current = currentInterval();
            if (current.type() == SessionType.WORK && !current.isPaused()) {
                seconds = Math.addExact(seconds, current.startedAt().secondsUntil(asOf));
            }
        }
        return Duration.ofSeconds(seconds);
    }
    public FocusSessionSnapshot snapshot() {
        return new FocusSessionSnapshot(id, sessionType, settings, createdAt, state, startedAt,
                endedAt, intervals.stream().map(SessionInterval::snapshot).toList());
    }

    public SessionId id() { return id; }
    public SessionType sessionType() { return sessionType; }
    public SessionSettings settings() { return settings; }
    public TimeStamp createdAt() { return createdAt; }
    public SessionState state() { return state; }
    public TimeStamp startedAt() { return startedAt; }
    public TimeStamp endedAt() { return endedAt; }
    public List<SessionInterval> intervals() { return List.copyOf(intervals); }
}
