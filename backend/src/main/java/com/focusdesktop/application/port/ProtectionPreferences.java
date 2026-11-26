package com.focusdesktop.application.port;

import java.time.Instant;

public interface ProtectionPreferences {
    State get();
    State setApplicationsEnabled(boolean enabled, String consent);
    record State(boolean applicationsEnabled, Instant applicationConsentAt) { }
}
