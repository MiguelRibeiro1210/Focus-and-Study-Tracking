package com.focusdesktop.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TimerProfileId(UUID value) {
    public TimerProfileId {
        Objects.requireNonNull(value, "value");
    }

    public static TimerProfileId create() {
        return new TimerProfileId(UUID.randomUUID());
    }
}
