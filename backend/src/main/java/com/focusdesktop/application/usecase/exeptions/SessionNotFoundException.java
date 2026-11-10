package com.focusdesktop.application.usecase.exeptions;

import com.focusdesktop.domain.model.SessionId;
import java.util.Objects;

public final class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(SessionId sessionId) {
        super("Session not found: " + Objects.requireNonNull(sessionId, "sessionId").value());
    }
}
