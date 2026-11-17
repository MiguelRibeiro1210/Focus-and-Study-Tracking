package com.focusdesktop.application.usecase.commands;

import com.focusdesktop.domain.model.SessionType;
import java.util.Objects;

public record StartSessionCommand(String projectName, SessionType sessionType) {
    public StartSessionCommand {
        Objects.requireNonNull(projectName, "projectName");
        projectName = projectName.trim();
        if (projectName.isEmpty() || projectName.length() > 120) {
            throw new IllegalArgumentException("projectName must contain between 1 and 120 characters");
        }
        Objects.requireNonNull(sessionType, "sessionType");
    }
}
