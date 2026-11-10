package com.focusdesktop.application.usecase.commands;

import com.focusdesktop.domain.model.SessionId;
import java.util.Objects;

public record SessionIdCommand(SessionId sessionId) {
    public SessionIdCommand {
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
