package com.focusdesktop.application.port;

import java.time.Instant;

public interface WebsiteProtectionPreferences {
    State get();
    State setEnabled(boolean enabled, String consent);
    record State(boolean enabled, Instant consentAt) { }
}
