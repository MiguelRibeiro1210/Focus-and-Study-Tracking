package com.focusdesktop.application.port;

import com.focusdesktop.domain.model.AppIdentifier;
import java.time.Instant;
import java.util.Optional;

public interface ActiveApplicationPort {
    Optional<Observation> currentApplication();

    record Observation(long pid, Instant startedAt, AppIdentifier identity) { }

    /** Only fixed, safe diagnostic messages belong in this exception. */
    class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }
}
