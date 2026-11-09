package com.focusdesktop.domain.model;

import java.util.Objects;
import java.util.UUID;

public record IntervalId(UUID value) {
    public IntervalId {
        Objects.requireNonNull(value, "value");
    }

    public static IntervalId generate() {
        return new IntervalId(UUID.randomUUID());
    }
}
