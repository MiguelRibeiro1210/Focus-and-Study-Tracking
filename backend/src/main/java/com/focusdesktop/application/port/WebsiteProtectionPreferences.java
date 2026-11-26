package com.focusdesktop.application.port;

import java.time.Instant;

public interface WebsiteProtectionPreferences {
    enum RoutingMethod { SYSTEM_PROXY, BRAVE }
    State get();
    State setEnabled(boolean enabled, String consent);
    default State setEnabled(boolean enabled, String consent, RoutingMethod method) {
        if (method != get().routingMethod()) throw new IllegalArgumentException("Routing selection is unavailable");
        return setEnabled(enabled, consent);
    }
    record State(boolean enabled, Instant consentAt, RoutingMethod routingMethod) {
        public State(boolean enabled, Instant consentAt) { this(enabled, consentAt, RoutingMethod.SYSTEM_PROXY); }
    }
}
