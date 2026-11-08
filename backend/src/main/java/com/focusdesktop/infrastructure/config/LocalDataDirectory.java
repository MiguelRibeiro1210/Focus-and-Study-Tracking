package com.focusdesktop.infrastructure.config;

import java.nio.file.Path;
import java.util.Locale;

public final class LocalDataDirectory {

    private static final String APPLICATION_DIRECTORY = "FocusDesktop";
    private static final String DATA_DIRECTORY_OVERRIDE = "FOCUSDESKTOP_DATA_DIR";

    private LocalDataDirectory() {}

    public static Path resolve() {
        var override = System.getenv(DATA_DIRECTORY_OVERRIDE);
        if (override != null && !override.isBlank()) {
            var overridePath = Path.of(override);
            var userHome = Path.of(System.getProperty("user.home"));
            return (overridePath.isAbsolute() ? overridePath : userHome.resolve(overridePath))
                    .normalize();
        }

        var userHome = Path.of(System.getProperty("user.home"));
        var operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (operatingSystem.contains("windows")) {
            var localAppData = System.getenv("LOCALAPPDATA");
            return localAppData == null || localAppData.isBlank()
                    ? userHome.resolve("AppData").resolve("Local").resolve(APPLICATION_DIRECTORY)
                    : Path.of(localAppData).resolve(APPLICATION_DIRECTORY);
        }

        if (operatingSystem.contains("mac") || operatingSystem.contains("darwin")) {
            return userHome.resolve("Library")
                    .resolve("Application Support")
                    .resolve(APPLICATION_DIRECTORY);
        }

        var xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            var xdgPath = Path.of(xdgDataHome);
            if (xdgPath.isAbsolute()) {
                return xdgPath.resolve("focusdesktop");
            }
        }

        return userHome.resolve(".local").resolve("share").resolve("focusdesktop");
    }
}
