package com.focusdesktop.application.usecase.commands;

import com.focusdesktop.domain.model.SessionType;
import java.util.Objects;

public record StartSessionCommand(SessionType sessionType) {
    public StartSessionCommand {
        Objects.requireNonNull(sessionType, "sessionType");
    }
}
