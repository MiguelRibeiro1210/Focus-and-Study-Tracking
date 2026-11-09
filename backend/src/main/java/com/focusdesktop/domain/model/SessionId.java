package com.focusdesktop.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {
    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }
}
