package com.focusdesktop.domain.model;

import java.time.Instant;
import java.util.Objects;

/** UTC instant used by the session domain. */
public record TimeStamp(Instant value) implements Comparable<TimeStamp> {
    public TimeStamp {
        Objects.requireNonNull(value, "value");
    }

    public static TimeStamp now() {
        return new TimeStamp(Instant.now());
    }

    public long secondsUntil(TimeStamp later) {
        Objects.requireNonNull(later, "later");
        if (later.value.isBefore(value)) {
            throw new IllegalArgumentException("Timestamp cannot precede the starting timestamp");
        }
        return java.time.Duration.between(value, later.value).getSeconds();
    }

    @Override
    public int compareTo(TimeStamp other) {
        return value.compareTo(other.value);
    }
}
