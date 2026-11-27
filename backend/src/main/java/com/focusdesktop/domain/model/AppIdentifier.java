package com.focusdesktop.domain.model;

import java.util.Objects;

/** An observed identity; transient process handles belong to the adapter, not this value. */
public record AppIdentifier(TargetPlatform platform, String executablePath, String processName, String applicationId) {
    public AppIdentifier(TargetPlatform platform, String executablePath, String processName) {
        this(platform, executablePath, processName, null);
    }
    public AppIdentifier {
        Objects.requireNonNull(platform, "platform");
        if (executablePath == null && processName == null && applicationId == null) {
            throw new IllegalArgumentException("An observation needs a path, process name or application identity");
        }
        if (executablePath != null) {
            executablePath = executablePath.trim();
            ApplicationTarget.normalize(platform, ApplicationMatchKind.EXECUTABLE_PATH, executablePath);
        }
        if (processName != null) {
            processName = processName.trim();
            ApplicationTarget.normalize(platform, ApplicationMatchKind.PROCESS_NAME, processName);
        }
        if (applicationId != null) applicationId = ApplicationIds.validate(platform, applicationId);
    }
}
