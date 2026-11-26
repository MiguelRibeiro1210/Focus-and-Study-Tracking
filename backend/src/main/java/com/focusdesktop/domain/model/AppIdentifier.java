package com.focusdesktop.domain.model;

import java.util.Objects;

/** An observed identity; transient process handles belong to the adapter, not this value. */
public record AppIdentifier(TargetPlatform platform, String executablePath, String processName) {
    public AppIdentifier {
        Objects.requireNonNull(platform, "platform");
        if (executablePath == null && processName == null) {
            throw new IllegalArgumentException("An observation needs a path or process name");
        }
        if (executablePath != null) {
            executablePath = executablePath.trim();
            ApplicationTarget.normalize(platform, ApplicationMatchKind.EXECUTABLE_PATH, executablePath);
        }
        if (processName != null) {
            processName = processName.trim();
            ApplicationTarget.normalize(platform, ApplicationMatchKind.PROCESS_NAME, processName);
        }
    }
}
